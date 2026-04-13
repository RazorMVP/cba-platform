package com.cba.customer;

import com.cba.common.response.ApiResponse;
import com.cba.customer.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer onboarding, KYC, staff assignment, and inter-branch transfers")
@SecurityRequirement(name = "oauth2")
public class CustomerController {

    private final CustomerService customerService;

    // ── Create ───────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Create a new customer and initiate KYC workflow")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {

        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getCustomer(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all customers with pagination")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> listCustomers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<CustomerResponse> page = customerService.listCustomers(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
                ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    // ── Update profile ────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Update customer profile fields")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(customerService.updateCustomer(id, request)));
    }

    // ── KYC status (legacy — kept for backward compat) ────────────────────────

    @PutMapping("/{id}/kyc-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Update customer KYC status (direct override)")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateKycStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKycStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(customerService.updateKycStatus(id, request)));
    }

    // ── Commands (Mifos pattern) ──────────────────────────────────────────────

    /**
     * POST /api/v1/customers/{id}?command=activate|reject|withdraw|reactivate|
     *   undoRejection|undoWithdrawal|suspend|close|
     *   assignStaff|unassignStaff|
     *   proposeTransfer|acceptTransfer|rejectTransfer|withdrawTransfer
     */
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Execute a lifecycle command on a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> executeCommand(
            @PathVariable UUID id,
            @RequestParam String command,
            @RequestBody(required = false) CustomerCommandRequest payload) {

        CustomerCommandRequest body = payload != null ? payload : new CustomerCommandRequest();
        CustomerResponse response = customerService.executeCommand(id, command, body);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Delete (PENDING_KYC only) ─────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a pending customer (PENDING_KYC status only)")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
