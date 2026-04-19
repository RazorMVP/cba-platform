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

@Tag(name = "Fixed Deposit Products", description = "Term deposit product catalogue — define rates, term limits and penalty rules")
@RestController
@RequestMapping("/api/v1/fixeddepositproducts")
@RequiredArgsConstructor
public class FixedDepositProductController {

    private final FixedDepositService service;

    @Operation(summary = "List all fixed deposit products")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<FixedDepositProduct>> list(Pageable pageable) {
        return ApiResponse.ok(service.listProducts(pageable));
    }

    @Operation(summary = "Get a fixed deposit product by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FixedDepositProduct> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @Operation(summary = "Create a new fixed deposit product")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FixedDepositProduct> create(@RequestBody FixedDepositService.CreateFdProductRequest req) {
        return ApiResponse.ok(service.createProduct(req));
    }

    @Operation(summary = "Update a fixed deposit product")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FixedDepositProduct> update(@PathVariable UUID id, @RequestBody FixedDepositService.CreateFdProductRequest req) {
        return ApiResponse.ok(service.updateProduct(id, req));
    }

    @Operation(summary = "Delete a fixed deposit product")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteProduct(id);
    }
}
