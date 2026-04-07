package com.cba.report;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Dynamic SQL report engine — list, run, and manage reports")
public class ReportController {

    private final ReportService reportService;

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
}
