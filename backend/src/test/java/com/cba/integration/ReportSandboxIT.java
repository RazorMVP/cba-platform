package com.cba.integration;

import com.cba.common.exception.CbaException;
import com.cba.report.ReportingDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backlog 10 against real PostgreSQL: report SQL runs as the read-only {@code cba_reporting} role over
 * the {@code reporting} views. The attacks below go straight to {@link ReportingDatabase} — <b>the
 * parser is bypassed</b> — so they prove the database boundary holds on its own; the HTTP cases prove the
 * parser refuses them first. (Test profile: row cap 50, timeout 3 s, role password set.)
 */
@DisplayName("Report SQL sandbox — the database role is the boundary")
class ReportSandboxIT extends AbstractIntegrationTest {

    @Autowired ReportingDatabase reportingDatabase;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SELECT * FROM public.platform_users",
            "SELECT first_name_encrypted FROM public.customers",
            "SELECT key_hash FROM public.partner_api_keys",
            "SELECT old_values FROM public.audit_log",
            "SELECT keycloak_id FROM customers",
            "DELETE FROM public.reports",
            "WITH gone AS (DELETE FROM loans RETURNING id) SELECT * FROM gone",
            "SELECT * INTO stolen FROM loans",
            "SELECT set_config('role', 'cba_user', false)",
            "SELECT 1; SELECT * FROM public.reports"
    })
    @DisplayName("without the parser, the role alone refuses it")
    void roleRefuses(String sql) {
        Integer reportsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class);

        assertThatThrownBy(() -> reportingDatabase.query(sql, new MapSqlParameterSource()))
                .isInstanceOf(CbaException.class)
                .hasFieldOrPropertyWithValue("errorCode", "REPORT_SQL_FAILED");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports", Integer.class)).isEqualTo(reportsBefore);
    }

    @Test
    @DisplayName("a query running too long is cancelled by the statement timeout")
    void timeout() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> reportingDatabase.query("SELECT pg_sleep(20)", new MapSqlParameterSource()))
                .hasFieldOrPropertyWithValue("errorCode", "REPORT_SQL_FAILED");
        assertThat((System.nanoTime() - start) / 1_000_000_000L).isLessThan(15);
    }

    @Test
    @DisplayName("a setting a report changes on its session doesn't reach the next report (new connection each time)")
    void sessionNotShared() {
        reportingDatabase.query("SELECT set_config('statement_timeout', '0', false) AS t", new MapSqlParameterSource());

        ReportingDatabase.ReportRows next = reportingDatabase.query(
                "SELECT current_setting('statement_timeout') AS t, current_setting('transaction_read_only') AS ro",
                new MapSqlParameterSource());
        assertThat(next.rows().get(0).get("t")).isEqualTo("3s");
        assertThat(next.rows().get(0).get("ro")).isEqualTo("on");
    }

    @Test
    @DisplayName("one huge value is refused by the result byte cap, not loaded into memory")
    void byteCap() {
        assertThatThrownBy(() -> reportingDatabase.query("SELECT repeat('x', 5000000) AS big", new MapSqlParameterSource()))
                .hasFieldOrPropertyWithValue("errorCode", "REPORT_SQL_FAILED");
    }

    @Test
    @DisplayName("more rows than the cap: the first 50, marked truncated")
    void rowCap() {
        ReportingDatabase.ReportRows r = reportingDatabase.query("SELECT generate_series(1, 100) AS n", new MapSqlParameterSource());
        assertThat(r.rows()).hasSize(50);
        assertThat(r.truncated()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ActiveLoans", "LoansInArrears", "SavingsBalance", "TellerCashPosition",
            "CustomerAcquisition", "TrialBalance", "LoanProductSummary"})
    @DisplayName("every core report runs in the sandbox")
    void coreReportsRun(String name) {
        String url = "/api/v1/runreports/" + name + ("CustomerAcquisition".equals(name) ? "?R_startDate=2020-01-01" : "");
        ResponseEntity<String> r = rest.getForEntity(url, String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(r.getHeaders().getFirst("X-Report-Truncated")).isIn("true", "false");
    }

    @Test
    @DisplayName("a DATE parameter that isn't a date: 400 INVALID_PARAM_VALUE (it used to be pasted into the SQL)")
    void dateParamNotInjected() {
        ResponseEntity<String> r = rest.getForEntity(
                "/api/v1/runreports/CustomerAcquisition?R_startDate={d}", String.class, "2020-01-01) OR (1=1");
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(400);
        assertThat(r.getBody()).contains("INVALID_PARAM_VALUE");
    }

    private ResponseEntity<Map> create(String name, String sql, List<Map<String, Object>> params) {
        return rest.postForEntity("/api/v1/reports", Map.of("reportName", name, "reportCategory", "CUSTOMER",
                "reportSql", sql, "reportParameters", params), Map.class);
    }

    @Test
    @DisplayName("create → disabled; enable by the creator 403; by another admin 200; a STRING parameter is bound, not pasted")
    void createEnableRun() {
        String name = "It" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map> created = create(name,
                "SELECT kyc_status, COUNT(*) AS n FROM customers WHERE kyc_status = :status GROUP BY kyc_status",
                List.of(Map.of("parameterName", "status", "parameterType", "STRING", "optional", false)));
        assertThat(created.getStatusCode().value()).as(String.valueOf(created.getBody())).isEqualTo(201);
        Map<?, ?> data = (Map<?, ?>) created.getBody().get("data");
        assertThat(data.get("useReport")).isEqualTo(false);
        String id = (String) data.get("id");

        ResponseEntity<String> self = rest.postForEntity("/api/v1/reports/" + id + "/enable", null, String.class);
        assertThat(self.getStatusCode().value()).as(self.getBody()).isEqualTo(403);
        assertThat(self.getBody()).contains("SAME_USER");

        jdbc.update("UPDATE reports SET created_by = 'another-admin' WHERE id = ?::uuid", id);
        assertThat(rest.postForEntity("/api/v1/reports/" + id + "/enable", null, String.class).getStatusCode().value())
                .isEqualTo(200);

        ResponseEntity<String> injected = rest.getForEntity(
                "/api/v1/runreports/" + name + "?status={s}", String.class, "x' OR '1'='1");
        assertThat(injected.getStatusCode().value()).as(injected.getBody()).isEqualTo(200);
        assertThat(injected.getBody()).contains("\"data\":[]");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SELECT * FROM public.platform_users",
            "SELECT set_config('role', 'cba_user', false)",
            "DELETE FROM reports",
            "SELECT 1; SELECT 2",
            "SELECT * FROM pg_catalog.pg_authid",
            "SELECT \"set_config\"('statement_timeout', '0', false)",
            "SELECT pg_catalog.set_config('statement_timeout', '0', false)",
            "SELECT query_to_xml('select 1', true, true, '')"
    })
    @DisplayName("create: the parser refuses it before anything runs (400)")
    void parserRefuses(String sql) {
        ResponseEntity<Map> r = create("Bad" + UUID.randomUUID().toString().substring(0, 8), sql, List.of());
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(400);
        assertThat(String.valueOf(r.getBody())).containsAnyOf("INVALID_REPORT_SQL", "REPORT_SQL_NOT_SUPPORTED");
    }
}
