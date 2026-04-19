package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Funds", description = "Capital fund definitions — track the source of funds for loan products (e.g. donor funds, government schemes)")
@RestController
@RequestMapping("/api/v1/funds")
@RequiredArgsConstructor
public class FundsController {

    private final SystemConfigService service;

    @Operation(summary = "List all funds")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Fund>> list(Pageable pageable) {
        return ApiResponse.ok(service.listFunds(pageable));
    }

    @Operation(summary = "Get a fund by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Fund> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getFund(id));
    }

    @Operation(summary = "Create a new fund")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Fund> create(@RequestBody SystemConfigService.CreateFundRequest req) {
        return ApiResponse.ok(service.createFund(req));
    }

    @Operation(summary = "Update a fund")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Fund> update(@PathVariable UUID id, @RequestBody SystemConfigService.CreateFundRequest req) {
        return ApiResponse.ok(service.updateFund(id, req));
    }

    @Operation(summary = "Delete a fund")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteFund(id);
    }
}
