package com.cba.report;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
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
    @Mock JdbcTemplate jdbcTemplate;
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
        @DisplayName("createReport saves and returns report")
        void createReport_success() {
            Report r = buildReport("TestReport", "SELECT 1");
            when(reportRepository.findByReportName("TestReport")).thenReturn(Optional.empty());
            when(reportRepository.save(any())).thenReturn(r);

            Report result = service.createReport(r);
            assertThat(result.getReportName()).isEqualTo("TestReport");
            verify(auditLogService).log(eq("REPORT"), anyString(), eq("CREATED"), any(), anyString());
        }

        @Test
        @DisplayName("createReport throws when name already exists")
        void createReport_duplicateName_throws() {
            Report r = buildReport("TestReport", "SELECT 1");
            when(reportRepository.findByReportName("TestReport")).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.createReport(r))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("listReports returns enabled reports")
        void listReports_returnsEnabled() {
            Report r = buildReport("R1", "SELECT 1");
            when(reportRepository.findByEnabledTrue()).thenReturn(List.of(r));

            assertThat(service.listReports()).hasSize(1);
        }

        @Test
        @DisplayName("getReport returns report when found")
        void getReport_found() {
            Report r = buildReport("R1", "SELECT 1");
            when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

            assertThat(service.getReport(r.getId()).getReportName()).isEqualTo("R1");
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
    @DisplayName("Run Report")
    class RunReport {

        @Test
        @DisplayName("runReport executes SELECT SQL")
        void runReport_success() {
            Report r = buildReport("CustomerList", "SELECT * FROM customers");
            when(reportRepository.findByReportName("CustomerList")).thenReturn(Optional.of(r));
            when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("id", "1")));

            List<Map<String, Object>> result = service.runReport("CustomerList", Map.of());
            assertThat(result).hasSize(1);
            verify(auditLogService).log(eq("REPORT"), anyString(), eq("EXECUTED"), any(), anyString());
        }

        @Test
        @DisplayName("runReport throws when report not found")
        void runReport_notFound_throws() {
            when(reportRepository.findByReportName("Unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.runReport("Unknown", Map.of()))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("runReport throws when report is disabled")
        void runReport_disabled_throws() {
            Report r = buildReport("DisabledReport", "SELECT 1");
            r.setEnabled(false);
            when(reportRepository.findByReportName("DisabledReport")).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.runReport("DisabledReport", Map.of()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("runReport rejects non-SELECT SQL")
        void runReport_dmlSql_throws() {
            Report r = buildReport("DmlReport", "DELETE FROM customers");
            when(reportRepository.findByReportName("DmlReport")).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.runReport("DmlReport", Map.of()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("must start with SELECT");
        }

        @Test
        @DisplayName("runReport rejects parameter with injection characters")
        void runReport_injectionInParam_throws() {
            Report r = buildReport("ParamReport", "SELECT * FROM t WHERE x = ${val}");
            when(reportRepository.findByReportName("ParamReport")).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.runReport("ParamReport", Map.of("val", "'; DROP TABLE--")))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("forbidden characters");
        }

        @Test
        @DisplayName("runReport substitutes param placeholder with supplied value")
        void runReport_paramSubstitution() {
            Report r = buildReport("FilteredReport", "SELECT * FROM t WHERE id = ${customerId}");
            when(reportRepository.findByReportName("FilteredReport")).thenReturn(Optional.of(r));
            when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

            service.runReport("FilteredReport", Map.of("customerId", "123"));
            verify(jdbcTemplate).queryForList(contains("123"));
        }
    }
}
