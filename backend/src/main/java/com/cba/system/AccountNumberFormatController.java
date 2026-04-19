package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Account Number Formats", description = "Auto-generated account number format rules — configure prefix type per account type (LOAN, SAVINGS, CLIENT, SHARE)")
@RestController
@RequestMapping("/api/v1/accountnumberformats")
@RequiredArgsConstructor
public class AccountNumberFormatController {

    private final SystemConfigService service;

    @Operation(summary = "List all account number format entries")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AccountNumberFormat>> list() {
        return ApiResponse.ok(service.listAccountNumberFormats());
    }

    @Operation(summary = "Get an account number format by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountNumberFormat> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getAccountNumberFormat(id));
    }

    @Operation(summary = "Update an account number format entry")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountNumberFormat> update(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.UpdateAccountNumberFormatRequest req) {
        return ApiResponse.ok(service.updateAccountNumberFormat(id, req));
    }
}
