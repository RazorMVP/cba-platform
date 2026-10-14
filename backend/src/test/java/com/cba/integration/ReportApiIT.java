package com.cba.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reports API in Mifos's field names, over real HTTP. Until cont. 32 it returned the
 * JPA entity: {@code Report.parameters} ↔ {@code ReportParameter.report} recursed past
 * Jackson's nesting limit, so {@code GET /api/v1/reports} failed whenever a report had
 * parameters (CustomerAcquisition does), and the web read field names the entity didn't have.
 */
@DisplayName("Reports API — Mifos-shaped responses over real HTTP")
class ReportApiIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;

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
    @DisplayName("no create endpoint: user-supplied report SQL is not accepted (core reports come from migrations)")
    void createIsNotExposed() {
        ResponseEntity<String> r = rest.postForEntity("/api/v1/reports",
                Map.of("reportName", "ItNope", "reportSql", "SELECT 1"), String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(405);
        assertThat(r.getHeaders().getAllow()).contains(org.springframework.http.HttpMethod.GET)
                .doesNotContain(org.springframework.http.HttpMethod.POST);
    }

    private static List<String> names(JsonNode data) {
        return StreamSupport.stream(data.spliterator(), false).map(n -> n.get("reportName").asText()).toList();
    }

    private static JsonNode byName(JsonNode data, String name) {
        return StreamSupport.stream(data.spliterator(), false)
                .filter(n -> name.equals(n.get("reportName").asText())).findFirst().orElseThrow();
    }
}
