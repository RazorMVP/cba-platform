package com.cba.product;

import com.cba.common.response.ApiResponse;
import com.cba.product.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "oauth2")
@Tag(name = "Products", description = "Loan and Deposit product catalogue management")
public class ProductController {

    private final ProductService productService;

    // ── Loan Products ────────────────────────────────────────────────

    @GetMapping("/api/v1/loan-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "List loan products")
    public ResponseEntity<ApiResponse<List<LoanProductResponse>>> getLoanProducts(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAllLoanProducts(activeOnly)));
    }

    @GetMapping("/api/v1/loan-products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get a loan product by ID")
    public ResponseEntity<ApiResponse<LoanProductResponse>> getLoanProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getLoanProduct(id)));
    }

    @PostMapping("/api/v1/loan-products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new loan product")
    public ResponseEntity<ApiResponse<LoanProductResponse>> createLoanProduct(
            @Valid @RequestBody LoanProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.createLoanProduct(request)));
    }

    @PutMapping("/api/v1/loan-products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing loan product")
    public ResponseEntity<ApiResponse<LoanProductResponse>> updateLoanProduct(
            @PathVariable UUID id,
            @Valid @RequestBody LoanProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateLoanProduct(id, request)));
    }

    @DeleteMapping("/api/v1/loan-products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a loan product")
    public ResponseEntity<ApiResponse<Void>> deactivateLoanProduct(@PathVariable UUID id) {
        productService.deactivateLoanProduct(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Deposit Products ─────────────────────────────────────────────

    @GetMapping("/api/v1/deposit-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "List deposit products")
    public ResponseEntity<ApiResponse<List<DepositProductResponse>>> getDepositProducts(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAllDepositProducts(activeOnly)));
    }

    @GetMapping("/api/v1/deposit-products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get a deposit product by ID")
    public ResponseEntity<ApiResponse<DepositProductResponse>> getDepositProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getDepositProduct(id)));
    }

    @PostMapping("/api/v1/deposit-products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new deposit product")
    public ResponseEntity<ApiResponse<DepositProductResponse>> createDepositProduct(
            @Valid @RequestBody DepositProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.createDepositProduct(request)));
    }

    @PutMapping("/api/v1/deposit-products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing deposit product")
    public ResponseEntity<ApiResponse<DepositProductResponse>> updateDepositProduct(
            @PathVariable UUID id,
            @Valid @RequestBody DepositProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateDepositProduct(id, request)));
    }

    @DeleteMapping("/api/v1/deposit-products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a deposit product")
    public ResponseEntity<ApiResponse<Void>> deactivateDepositProduct(@PathVariable UUID id) {
        productService.deactivateDepositProduct(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
