package com.cba.bulkimport;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Tag(name = "Bulk Import", description = "Batch CSV ingestion for customers and loans")
@RestController
@RequestMapping("/api/v1/bulkimport")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BulkImportController {

    private final BulkImportService svc;

    @Operation(summary = "Import customers from CSV",
               description = "Columns: firstName*, lastName*, email*, phone, nationalId, dateOfBirth (yyyy-MM-dd), notes")
    @PostMapping(value = "/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BulkImportResult> importCustomers(
            @RequestPart("file") MultipartFile file,
            Authentication auth) throws Exception {
        return ApiResponse.ok(svc.importCustomers(file, auth));
    }

    @Operation(summary = "Import loans from CSV",
               description = "Columns: customerId*, productId*, linkedAccountId*, principalAmount*, termMonths, notes")
    @PostMapping(value = "/loans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BulkImportResult> importLoans(
            @RequestPart("file") MultipartFile file,
            Authentication auth) throws Exception {
        return ApiResponse.ok(svc.importLoans(file, auth));
    }

    @Operation(summary = "List recent import jobs (last 20)")
    @GetMapping("/jobs")
    public ApiResponse<List<BulkImportJob>> recentJobs(
            @RequestParam(required = false) String entityType) {
        return ApiResponse.ok(svc.recentJobs(entityType));
    }

    @Operation(summary = "Download CSV template",
               description = "entityType: CUSTOMERS or LOANS")
    @GetMapping(value = "/templates/{entityType}", produces = "text/csv")
    public String downloadTemplate(@PathVariable String entityType) {
        return switch (entityType.toUpperCase(Locale.ROOT)) {
            case "CUSTOMERS" -> "firstName,lastName,email,phone,nationalId,dateOfBirth,notes\n" +
                                "John,Doe,john.doe@example.com,+254712345678,ID123456,1990-05-15,\n";
            case "LOANS"     -> "customerId,productId,linkedAccountId,principalAmount,termMonths,notes\n" +
                                "<uuid>,<uuid>,<uuid>,50000.00,24,\n";
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }
}
