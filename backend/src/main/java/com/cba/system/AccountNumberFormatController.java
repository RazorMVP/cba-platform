package com.cba.system;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accountnumberformats")
@RequiredArgsConstructor
public class AccountNumberFormatController {

    private final SystemConfigService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AccountNumberFormat>> list() {
        return ApiResponse.ok(service.listAccountNumberFormats());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountNumberFormat> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getAccountNumberFormat(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountNumberFormat> update(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.UpdateAccountNumberFormatRequest req) {
        return ApiResponse.ok(service.updateAccountNumberFormat(id, req));
    }
}
