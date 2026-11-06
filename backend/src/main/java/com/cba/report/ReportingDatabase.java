package com.cba.report;

import com.cba.common.exception.CbaException;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Report SQL runs here, as the read-only login role {@code cba_reporting} (backlog 10) — never as the
 * application user, which is the database owner.
 *
 * <p><b>Login.</b> V66 creates the role NOLOGIN with no password. At startup, when
 * {@code REPORTING_DB_PASSWORD} ({@code app.reports.db.password}) is set, the role gets LOGIN and that
 * password through pgjdbc's {@link PGConnection#alterUserPassword}, which computes the SCRAM verifier on
 * the client — the password never appears in SQL text or the server log. It's set on every start, so
 * rotating the secret and restarting is enough. Unset → report runs answer {@code 503 REPORTING_NOT_CONFIGURED}.
 *
 * <p><b>Connection.</b> A new connection per report, closed afterwards — never pooled, so nothing a
 * report does to its session ({@code set_config(…, false)}, a changed search path or read-only flag)
 * can reach the next report. At most {@code app.reports.max-concurrent} run at once ({@code 503 REPORTS_BUSY}
 * after a short wait). Not a {@link DataSource} bean, so Boot's main DataSource, Flyway and Quartz are
 * untouched. Every query has a timeout (client-side cancel and the session's {@code statement_timeout}),
 * a row cap ({@code app.reports.max-rows}; one row more is read to report truncation) and a byte cap
 * (pgjdbc {@code maxResultBuffer}, {@code app.reports.max-result-bytes}) so one huge value can't exhaust memory.
 */
@Slf4j
@Component
public class ReportingDatabase {

    /** Rows of a report run; {@code truncated} when there were more than the cap. */
    public record ReportRows(List<Map<String, Object>> rows, boolean truncated, int rowLimit) {}

    private final DataSource appDataSource;
    private final String url;
    private final String username;
    private final String password;
    private final int maxRows;
    private final int queryTimeoutSeconds;
    private final String maxResultBytes;
    private final Semaphore slots;

    private volatile boolean configured;

    public ReportingDatabase(DataSource appDataSource,
                             @Value("${spring.datasource.url}") String url,
                             @Value("${app.reports.db.username:cba_reporting}") String username,
                             @Value("${app.reports.db.password:}") String password,
                             @Value("${app.reports.max-rows:10000}") int maxRows,
                             @Value("${app.reports.query-timeout-seconds:30}") int queryTimeoutSeconds,
                             @Value("${app.reports.max-result-bytes:20M}") String maxResultBytes,
                             @Value("${app.reports.max-concurrent:3}") int maxConcurrent) {
        this.appDataSource = appDataSource;
        this.url = url;
        this.username = username;
        this.password = password;
        this.maxRows = maxRows;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.maxResultBytes = maxResultBytes;
        this.slots = new Semaphore(Math.max(1, maxConcurrent));
    }

    /** Give the role LOGIN and the configured password (as the application user). */
    @EventListener(ApplicationReadyEvent.class)
    public void initialiseRole() {
        if (password == null || password.isBlank()) {
            log.warn("REPORTING_DB_PASSWORD is not set: reports can't run (503 REPORTING_NOT_CONFIGURED)");
            return;
        }
        if (!username.matches("[a-z_][a-z0-9_]*")) {
            log.error("app.reports.db.username '{}' is not a plain role name; reports disabled", username);
            return;
        }
        try (Connection c = appDataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER ROLE " + username + " LOGIN");
            c.unwrap(PGConnection.class).alterUserPassword(username, password.toCharArray(), "scram-sha-256");
            configured = true;
            log.info("Reporting role {} ready", username);
        } catch (SQLException | RuntimeException e) {
            log.error("Could not set up the reporting role {}: {} — reports disabled", username, e.getMessage());
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public int rowLimit() {
        return maxRows;
    }

    /** Run {@code sql} with named parameters as the reporting role. 400 REPORT_SQL_FAILED on a database error. */
    public ReportRows query(String sql, MapSqlParameterSource params) {
        NamedParameterJdbcTemplate jdbc = template(maxRows + 1);
        acquire();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
            boolean truncated = rows.size() > maxRows;
            return new ReportRows(truncated ? List.copyOf(rows.subList(0, maxRows)) : rows, truncated, maxRows);
        } catch (DataAccessException e) {
            throw failed(e);
        } finally {
            slots.release();
        }
    }

    /** {@code EXPLAIN} the SQL as the reporting role: proves it compiles against the views, reads nothing. */
    public void explain(String sql, MapSqlParameterSource params) {
        NamedParameterJdbcTemplate jdbc = template(0);
        acquire();
        try {
            jdbc.queryForList("EXPLAIN " + sql, params);
        } catch (DataAccessException e) {
            throw failed(e);
        } finally {
            slots.release();
        }
    }

    private void acquire() {
        try {
            if (!slots.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new CbaException("REPORTS_BUSY", "Too many reports running; try again shortly",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CbaException("REPORTS_BUSY", "Interrupted while waiting to run the report",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private NamedParameterJdbcTemplate template(int rowCap) {
        if (!configured) {
            throw new CbaException("REPORTING_NOT_CONFIGURED",
                    "Reports are unavailable: the reporting database role is not configured",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        JdbcTemplate jdbc = new JdbcTemplate(connections());
        jdbc.setQueryTimeout(queryTimeoutSeconds);
        if (rowCap > 0) jdbc.setMaxRows(rowCap);
        return new NamedParameterJdbcTemplate(jdbc);
    }

    /** A new connection for every call (closed after it), as the reporting role, with the result byte cap. */
    private DataSource connections() {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("maxResultBuffer", maxResultBytes);
        props.setProperty("readOnly", "true");
        props.setProperty("options", "-c statement_timeout=" + queryTimeoutSeconds + "s -c default_transaction_read_only=on");
        props.setProperty("ApplicationName", "cba-reporting");
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriverClass(org.postgresql.Driver.class);
        ds.setUrl(url);
        ds.setConnectionProperties(props);
        return ds;
    }

    private static CbaException failed(DataAccessException e) {
        Throwable root = e.getMostSpecificCause();
        String message = root.getMessage() == null ? "Report SQL failed" : root.getMessage().lines().findFirst().orElse("");
        return CbaException.badRequest("REPORT_SQL_FAILED", message);
    }
}
