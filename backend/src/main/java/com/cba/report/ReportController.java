package com.cba.report;

import com.cba.common.response.ApiResponse;
import com.cba.report.dto.ReportRequest;
import com.cba.report.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Dynamic SQL report engine — list, run, and manage reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    @GetMapping("/api/v1/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all enabled reports",
               description = "Mifos field names: reportCategory (LOAN|SAVINGS|TELLER|CUSTOMER|ACCOUNTING|MIXED), "
                       + "useReport, coreReport, reportParameters[] (optional = not required).")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> listReports(org.springframework.security.core.Authentication auth) {
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.ok(reportService.listReports(admin)));
    }

    @PostMapping("/api/v1/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a custom report (saved disabled)",
            description = "The SQL must be one SELECT over the reporting views (reporting.loans, accounts, customers "
                    + "without PII, …); parameters are :name placeholders, bound and typed by reportParameters. "
                    + "Validated and dry-run (EXPLAIN as the read-only reporting role) before saving. "
                    + "A different ADMIN must enable it: POST /api/v1/reports/{id}/enable.")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(@jakarta.validation.Valid @RequestBody ReportRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(reportService.createReport(request)));
    }

    @PostMapping("/api/v1/reports/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable a report", description = "Not by the ADMIN who created it (403 SAME_USER).")
    public ResponseEntity<ApiResponse<ReportResponse>> enableReport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.enableReport(id)));
    }

    @GetMapping("/api/v1/reports/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get report definition by ID")
    public ResponseEntity<ApiResponse<ReportResponse>> getReport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getReport(id)));
    }

    @DeleteMapping("/api/v1/reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a custom report (core reports cannot be deleted)")
    public ResponseEntity<ApiResponse<Void>> deleteReport(@PathVariable UUID id) {
        reportService.deleteReport(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Execute a report by name. Query parameters are passed as report parameters.
     * Example: GET /api/v1/runreports/ActiveLoans?currencyCode=USD&officeId=...
     */
    @GetMapping("/api/v1/runreports/{reportName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(
        summary = "Run a report by name",
        description = "Pass report parameters as query parameters (bound, typed: DATE yyyy-MM-dd, NUMBER, BOOLEAN). "
                + "Runs as the read-only reporting role with a timeout and a row cap; headers X-Report-Truncated "
                + "(true when there were more rows than X-Report-Row-Limit). 503 REPORTING_NOT_CONFIGURED when "
                + "the reporting role has no password."
    )
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> runReport(
            @PathVariable String reportName,
            @RequestParam Map<String, String> params) {
        ReportingDatabase.ReportRows result = reportService.runReport(reportName, params);
        return ResponseEntity.ok()
                .header("X-Report-Truncated", String.valueOf(result.truncated()))
                .header("X-Report-Row-Limit", String.valueOf(result.rowLimit()))
                .body(ApiResponse.ok(result.rows()));
    }

    @GetMapping("/api/v1/runreports/{reportName}/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(
        summary = "Export a report as CSV, XLSX, or PDF",
        description = "Pass report parameters as query params. Use ?format=csv|xlsx|pdf (default: csv)."
    )
    public ResponseEntity<byte[]> exportReport(
            @PathVariable String reportName,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam Map<String, String> params) throws IOException {

        // Remove 'format' from params so it isn't passed to the SQL engine
        Map<String, String> reportParams = new java.util.HashMap<>(params);
        reportParams.remove("format");

        List<Map<String, Object>> rows = reportService.runReport(reportName, reportParams).rows();

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "xlsx" -> {
                byte[] data = reportExportService.exportToXlsx(reportName, rows);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(reportName + ".xlsx").build().toString())
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(data);
            }
            case "pdf" -> {
                byte[] data = reportExportService.exportToPdf(reportName, rows);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(reportName + ".pdf").build().toString())
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(data);
            }
            default -> {
                byte[] data = reportExportService.exportToCsv(rows);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(reportName + ".csv").build().toString())
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(data);
            }
        };
    }
}
