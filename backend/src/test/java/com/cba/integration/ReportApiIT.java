package com.cba.integration;

import com.cba.report.ReportController;
import com.cba.report.dto.ReportRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reports API in Mifos's field names, over real HTTP. Until cont. 32 it returned the
 * JPA entity: {@code Report.parameters} ↔ {@code ReportParameter.report} recursed past
 * Jackson's nesting limit, so {@code GET /api/v1/reports} failed whenever a report had
 * parameters (CustomerAcquisition does), and the web read field names the entity didn't have.
 */
@DisplayName("Reports API — Mifos-shaped responses and custom report creation over real HTTP")
class ReportApiIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired ReportController reportController;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("list: 200, every seeded report, Mifos names (reportCategory, useReport, reportParameters.optional)")
    void listReports() throws Exception {
        ResponseEntity<String> r = rest.getForEntity("/api/v1/reports", String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);

        JsonNode data = json.readTree(r.getBody()).get("data");
        assertThat(names(data)).contains("ActiveLoans", "LoansInArrears", "SavingsBalance", "TellerCashPosition",
                "CustomerAcquisition", "TrialBalance", "LoanProductSummary");

        JsonNode acquisition = byName(data, "CustomerAcquisition");
        assertThat(acquisition.get("reportCategory").asText()).isEqualTo("CUSTOMER");
        assertThat(acquisition.get("coreReport").asBoolean()).isTrue();
        assertThat(acquisition.get("useReport").asBoolean()).isTrue();
        assertThat(acquisition.has("category")).isFalse();
        assertThat(acquisition.has("enabled")).isFalse();
        assertThat(acquisition.has("parameters")).isFalse();
        JsonNode params = acquisition.get("reportParameters");
        assertThat(params).hasSize(2);
        assertThat(params.get(0).get("parameterName").asText()).isEqualTo("R_startDate");
        assertThat(params.get(0).get("parameterLabel").asText()).isEqualTo("Start Date");
        assertThat(params.get(0).get("parameterType").asText()).isEqualTo("DATE");
        assertThat(params.get(0).get("optional").asBoolean()).isTrue();
        assertThat(params.get(0).has("report")).isFalse();

        assertThat(byName(data, "LoansInArrears").get("reportCategory").asText()).isEqualTo("LOAN");

        // Get by id returns the same shape.
        ResponseEntity<String> one = rest.getForEntity("/api/v1/reports/" + acquisition.get("id").asText(), String.class);
        assertThat(one.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(one.getBody()).get("data").get("reportParameters")).hasSize(2);
    }

    @Test
    @DisplayName("create: 201, listed, runnable with its parameter; invalid SQL/category/type/duplicate → 400")
    void createReport() throws Exception {
        String name = "ItCustom" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = Map.of(
                "reportName", name,
                "reportType", "TABLE",
                "reportCategory", "SAVINGS",
                "description", "accounts by currency",
                "reportSql", "SELECT currency_code, COUNT(*) AS n FROM accounts WHERE status = '${status}' GROUP BY currency_code",
                "reportParameters", List.of(Map.of("parameterName", "status", "parameterLabel", "Status",
                        "parameterType", "STRING", "defaultValue", "ACTIVE", "optional", true)));

        ResponseEntity<String> created = rest.postForEntity("/api/v1/reports", body, String.class);
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        JsonNode report = json.readTree(created.getBody()).get("data");
        assertThat(report.get("reportCategory").asText()).isEqualTo("SAVINGS");
        assertThat(report.get("coreReport").asBoolean()).isFalse();
        assertThat(report.get("reportParameters").get(0).get("optional").asBoolean()).isTrue();

        assertThat(names(json.readTree(rest.getForEntity("/api/v1/reports", String.class).getBody()).get("data")))
                .contains(name);
        ResponseEntity<String> ran = rest.getForEntity("/api/v1/runreports/" + name, String.class);
        assertThat(ran.getStatusCode().value()).as(ran.getBody()).isEqualTo(200);

        assertThat(errorCode(rest.postForEntity("/api/v1/reports", body, String.class)))
                .isEqualTo("REPORT_NAME_EXISTS");
        assertThat(errorCode(rest.postForEntity("/api/v1/reports",
                with(body, "reportSql", "DELETE FROM accounts"), String.class))).isEqualTo("INVALID_REPORT_SQL");
        assertThat(errorCode(rest.postForEntity("/api/v1/reports",
                with(body, "reportCategory", "Savings Accounts"), String.class))).isEqualTo("INVALID_REPORT_CATEGORY");
        assertThat(errorCode(rest.postForEntity("/api/v1/reports",
                with(body, "reportType", "Grid"), String.class))).isEqualTo("INVALID_REPORT_TYPE");

        // Custom reports can be deleted; core ones can't.
        String id = report.get("id").asText();
        assertThat(rest.exchange("/api/v1/reports/" + id, HttpMethod.DELETE, null, String.class)
                .getStatusCode().value()).isEqualTo(200);
        String coreId = byName(json.readTree(rest.getForEntity("/api/v1/reports", String.class).getBody()).get("data"),
                "ActiveLoans").get("id").asText();
        assertThat(errorCode(rest.exchange("/api/v1/reports/" + coreId, HttpMethod.DELETE, null, String.class)))
                .isEqualTo("CORE_REPORT");
    }

    @Test
    @DisplayName("create is ADMIN only: a TELLER is refused")
    void createReport_tellerRefused() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("t", null, "ROLE_TELLER"));
        assertThatThrownBy(() -> reportController.createReport(
                new ReportRequest("ItTeller", null, null, null, "SELECT 1", null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static List<String> names(JsonNode data) {
        return StreamSupport.stream(data.spliterator(), false).map(n -> n.get("reportName").asText()).toList();
    }

    private static JsonNode byName(JsonNode data, String name) {
        return StreamSupport.stream(data.spliterator(), false)
                .filter(n -> name.equals(n.get("reportName").asText())).findFirst().orElseThrow();
    }

    private String errorCode(ResponseEntity<String> r) throws Exception {
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(400);
        return json.readTree(r.getBody()).get("errors").get(0).get("code").asText();
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new java.util.HashMap<>(body);
        copy.put("reportName", "ItBad" + UUID.randomUUID().toString().substring(0, 8)); // past the duplicate check
        copy.put(key, value);
        return copy;
    }
}
