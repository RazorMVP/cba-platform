package com.cba.report;

import com.cba.common.exception.CbaException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
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
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

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
 * <p><b>Connection.</b> A small private Hikari pool, created on first use — deliberately not a
 * {@link DataSource} bean, so Boot's main DataSource, Flyway and Quartz are untouched. Every query
 * has a timeout and a row cap ({@code app.reports.max-rows}); one row more is read to tell the caller
 * the result was truncated.
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

    private volatile boolean configured;
    private volatile HikariDataSource pool;

    public ReportingDatabase(DataSource appDataSource,
                             @Value("${spring.datasource.url}") String url,
                             @Value("${app.reports.db.username:cba_reporting}") String username,
                             @Value("${app.reports.db.password:}") String password,
                             @Value("${app.reports.max-rows:10000}") int maxRows,
                             @Value("${app.reports.query-timeout-seconds:30}") int queryTimeoutSeconds) {
        this.appDataSource = appDataSource;
        this.url = url;
        this.username = username;
        this.password = password;
        this.maxRows = maxRows;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
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
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
            boolean truncated = rows.size() > maxRows;
            return new ReportRows(truncated ? List.copyOf(rows.subList(0, maxRows)) : rows, truncated, maxRows);
        } catch (DataAccessException e) {
            throw failed(e);
        }
    }

    /** {@code EXPLAIN} the SQL as the reporting role: proves it compiles against the views, reads nothing. */
    public void explain(String sql, MapSqlParameterSource params) {
        try {
            template(0).queryForList("EXPLAIN " + sql, params);
        } catch (DataAccessException e) {
            throw failed(e);
        }
    }

    private NamedParameterJdbcTemplate template(int rowCap) {
        if (!configured) {
            throw new CbaException("REPORTING_NOT_CONFIGURED",
                    "Reports are unavailable: the reporting database role is not configured",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        JdbcTemplate jdbc = new JdbcTemplate(pool());
        jdbc.setQueryTimeout(queryTimeoutSeconds);
        if (rowCap > 0) jdbc.setMaxRows(rowCap);
        return new NamedParameterJdbcTemplate(jdbc);
    }

    private HikariDataSource pool() {
        HikariDataSource p = pool;
        if (p == null) {
            synchronized (this) {
                if (pool == null) {
                    HikariConfig cfg = new HikariConfig();
                    cfg.setPoolName("cba-reporting");
                    cfg.setJdbcUrl(url);
                    cfg.setUsername(username);
                    cfg.setPassword(password);
                    cfg.setMaximumPoolSize(3);
                    cfg.setMinimumIdle(0);
                    cfg.setReadOnly(true);
                    cfg.setConnectionInitSql("SET statement_timeout = '" + queryTimeoutSeconds + "s'");
                    pool = new HikariDataSource(cfg);
                }
                p = pool;
            }
        }
        return p;
    }

    private static CbaException failed(DataAccessException e) {
        Throwable root = e.getMostSpecificCause();
        String message = root.getMessage() == null ? "Report SQL failed" : root.getMessage().lines().findFirst().orElse("");
        return CbaException.badRequest("REPORT_SQL_FAILED", message);
    }

    @PreDestroy
    void close() {
        if (pool != null) pool.close();
    }
}
