package com.cba.report;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.report.dto.ReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService — unit tests")
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock ReportingDatabase reportingDatabase;
    @org.mockito.Spy ReportSqlValidator sqlValidator = new ReportSqlValidator();
    @Mock org.springframework.data.domain.AuditorAware<String> auditorAware;
    @Mock AuditLogService auditLogService;

    @InjectMocks ReportService service;

    private Report buildReport(String name, String sql) {
        Report r = new Report();
        r.setId(UUID.randomUUID());
        r.setReportName(name);
        r.setReportSql(sql);
        r.setEnabled(true);
        r.setCoreReport(false);
        r.setParameters(new ArrayList<>());
        return r;
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        @DisplayName("listReports returns enabled reports")
        void listReports_returnsEnabled() {
            Report r = buildReport("R1", "SELECT 1");
            when(reportRepository.findByEnabledTrue()).thenReturn(List.of(r));

            assertThat(service.listReports(false)).hasSize(1);
        }

        @Test
        @DisplayName("getReport returns report when found")
        void getReport_found() {
            Report r = buildReport("R1", "SELECT 1");
            when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

            assertThat(service.getReport(r.getId()).reportName()).isEqualTo("R1");
        }

        @Test
        @DisplayName("getReport throws when not found")
        void getReport_notFound_throws() {
            UUID id = UUID.randomUUID();
            when(reportRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getReport(id))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("deleteReport removes non-core report")
        void deleteReport_success() {
            Report r = buildReport("R1", "SELECT 1");
            when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

            assertThatCode(() -> service.deleteReport(r.getId())).doesNotThrowAnyException();
            verify(reportRepository).delete(r);
        }

        @Test
        @DisplayName("deleteReport throws when report is core")
        void deleteReport_coreReport_throws() {
            Report r = buildReport("CoreReport", "SELECT 1");
            r.setCoreReport(true);
            when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.deleteReport(r.getId()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Core system reports cannot be deleted");
        }
    }

    @Nested
    @DisplayName("Run Report — sandboxed, parameters bound (backlog 10)")
    class RunReport {

        private ReportingDatabase.ReportRows rows(int n) {
            return new ReportingDatabase.ReportRows(
                    java.util.stream.IntStream.range(0, n).mapToObj(k -> Map.<String, Object>of("n", k)).toList(), false, 10000);
        }

        @Test
        @DisplayName("runs the stored SQL in the sandbox and audits it")
        void runReport_success() {
            Report r = buildReport("CustomerList", "SELECT id FROM customers");
            when(reportRepository.findByReportName("CustomerList")).thenReturn(Optional.of(r));
            when(reportingDatabase.query(eq("SELECT id FROM customers"), any())).thenReturn(rows(1));

            assertThat(service.runReport("CustomerList", Map.of()).rows()).hasSize(1);
            verify(auditLogService).log(eq("REPORT"), anyString(), eq("EXECUTED"), any(), anyString());
        }

        @Test
        @DisplayName("unknown report: 404; disabled: 400 REPORT_DISABLED")
        void runReport_notFoundOrDisabled() {
            when(reportRepository.findByReportName("Unknown")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.runReport("Unknown", Map.of())).isInstanceOf(CbaException.class);

            Report r = buildReport("DisabledReport", "SELECT 1");
            r.setEnabled(false);
            when(reportRepository.findByReportName("DisabledReport")).thenReturn(Optional.of(r));
            assertThatThrownBy(() -> service.runReport("DisabledReport", Map.of()))
                .hasFieldOrPropertyWithValue("errorCode", "REPORT_DISABLED");
            verifyNoInteractions(reportingDatabase);
        }

        @Test
        @DisplayName("stored SQL that isn't a single SELECT is refused before it reaches the database")
        void runReport_dmlSql_throws() {
            Report r = buildReport("DmlReport", "DELETE FROM customers");
            when(reportRepository.findByReportName("DmlReport")).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.runReport("DmlReport", Map.of()))
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_REPORT_SQL");
            verifyNoInteractions(reportingDatabase);
        }

        @Test
        @DisplayName("parameters are bound and typed, never pasted: an injection string is just a value")
        void runReport_paramsBound() {
            Report r = buildReport("ParamReport", "SELECT * FROM customers WHERE kyc_status = :status");
            ReportParameter p = new ReportParameter();
            p.setParameterName("status");
            p.setParameterType("STRING");
            r.getParameters().add(p);
            when(reportRepository.findByReportName("ParamReport")).thenReturn(Optional.of(r));
            when(reportingDatabase.query(anyString(), any())).thenReturn(rows(0));

            service.runReport("ParamReport", Map.of("status", "x' OR '1'='1"));

            var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class);
            verify(reportingDatabase).query(eq("SELECT * FROM customers WHERE kyc_status = :status"), captor.capture());
            assertThat(captor.getValue().getValue("status")).isEqualTo("x' OR '1'='1");
        }

        @Test
        @DisplayName("a DATE parameter must be a date; a required one must be given")
        void runReport_typedParams() {
            Report r = buildReport("DateReport", "SELECT * FROM customers WHERE created_at >= :from");
            ReportParameter p = new ReportParameter();
            p.setParameterName("from");
            p.setParameterType("DATE");
            p.setRequired(true);
            r.getParameters().add(p);
            when(reportRepository.findByReportName("DateReport")).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.runReport("DateReport", Map.of("from", "1) OR (1=1")))
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_PARAM_VALUE");
            assertThatThrownBy(() -> service.runReport("DateReport", Map.of()))
                .hasFieldOrPropertyWithValue("errorCode", "MISSING_REPORT_PARAM");
            verifyNoInteractions(reportingDatabase);
        }

        @Test
        @DisplayName("sqlParameters finds :name but not ::casts or text in quotes")
        void sqlParameters() {
            assertThat(ReportService.sqlParameters("SELECT x::date FROM t WHERE a = :a AND b = ':b' AND c = :c_1"))
                .containsExactly("a", "c_1");
        }
    }

    @Nested
    @DisplayName("Create and enable (backlog 10, D6a)")
    class CreateAndEnable {

        private com.cba.report.dto.ReportRequest request(String sql, List<com.cba.report.dto.ReportRequest.Parameter> params) {
            return new com.cba.report.dto.ReportRequest("MyReport", null, "LOAN", "d", sql, params);
        }

        @Test
        @DisplayName("create: validated, dry-run as the reporting role, saved DISABLED")
        void create_savedDisabled() {
            when(reportRepository.findByReportName("MyReport")).thenReturn(Optional.empty());
            when(reportRepository.save(any())).thenAnswer(inv -> { Report r = inv.getArgument(0); r.setId(UUID.randomUUID()); return r; });

            var r = service.createReport(request("SELECT status, COUNT(*) FROM loans WHERE status = :s GROUP BY status",
                    List.of(new com.cba.report.dto.ReportRequest.Parameter("s", "Status", "STRING", null, true))));

            assertThat(r.useReport()).isFalse();
            verify(reportingDatabase).explain(anyString(), any());
        }

        @Test
        @DisplayName("create: a :name without a parameter definition, or the old ${name}, is refused")
        void create_parameterRules() {
            when(reportRepository.findByReportName("MyReport")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.createReport(request("SELECT * FROM loans WHERE status = :s", List.of())))
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_REPORT_PARAMETER");
            assertThatThrownBy(() -> service.createReport(request("SELECT * FROM loans WHERE status = ${s}", List.of())))
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_REPORT_SQL");
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("enable: not by the creator (403 SAME_USER); by another admin → enabled")
        void enable_fourEyes() {
            Report r = buildReport("MyReport", "SELECT 1 AS one");
            r.setId(UUID.randomUUID());
            r.setEnabled(false);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "createdBy", "alice");
            when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
            when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("alice"), Optional.of("bob"));

            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken("alice", null, "ROLE_ADMIN"));
            try {
                assertThatThrownBy(() -> service.enableReport(r.getId()))
                    .hasFieldOrPropertyWithValue("errorCode", "SAME_USER");
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.TestingAuthenticationToken("bob", null, "ROLE_ADMIN"));
                assertThat(service.enableReport(r.getId()).useReport()).isTrue();
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }
    }
}
