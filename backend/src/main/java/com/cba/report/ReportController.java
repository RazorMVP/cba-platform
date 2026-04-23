package com.cba.report;

import com.cba.common.response.ApiResponse;
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
    @Operation(summary = "List all enabled reports")
    public ResponseEntity<ApiResponse<List<Report>>> listReports() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.listReports()));
    }

    @GetMapping("/api/v1/reports/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get report definition by ID")
    public ResponseEntity<ApiResponse<Report>> getReport(@PathVariable UUID id) {
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
        description = "Pass report parameters as query parameters. Returns rows as JSON array."
    )
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> runReport(
            @PathVariable String reportName,
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.runReport(reportName, params)));
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

        List<Map<String, Object>> rows = reportService.runReport(reportName, reportParams);

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
