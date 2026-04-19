package com.cba.deposit;

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

@Tag(name = "Recurring Deposit Products", description = "Recurring savings plan product catalogue — installment frequency, term limits and maturity rules")
@RestController
@RequestMapping("/api/v1/recurringdepositproducts")
@RequiredArgsConstructor
public class RecurringDepositProductController {

    private final RecurringDepositService service;

    @Operation(summary = "List all recurring deposit products")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<RecurringDepositProduct>> list(Pageable pageable) {
        return ApiResponse.ok(service.listProducts(pageable));
    }

    @Operation(summary = "Get a recurring deposit product by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RecurringDepositProduct> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @Operation(summary = "Create a new recurring deposit product")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RecurringDepositProduct> create(@RequestBody RecurringDepositService.CreateRdProductRequest req) {
        return ApiResponse.ok(service.createProduct(req));
    }

    @Operation(summary = "Update a recurring deposit product")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RecurringDepositProduct> update(@PathVariable UUID id, @RequestBody RecurringDepositService.CreateRdProductRequest req) {
        return ApiResponse.ok(service.updateProduct(id, req));
    }

    @Operation(summary = "Delete a recurring deposit product")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteProduct(id);
    }
}
