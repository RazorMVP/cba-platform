package com.cba.system;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/funds")
@RequiredArgsConstructor
public class FundsController {

    private final SystemConfigService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Fund>> list(Pageable pageable) {
        return ApiResponse.ok(service.listFunds(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Fund> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getFund(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Fund> create(@RequestBody SystemConfigService.CreateFundRequest req) {
        return ApiResponse.ok(service.createFund(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Fund> update(@PathVariable UUID id, @RequestBody SystemConfigService.CreateFundRequest req) {
        return ApiResponse.ok(service.updateFund(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteFund(id);
    }
}
