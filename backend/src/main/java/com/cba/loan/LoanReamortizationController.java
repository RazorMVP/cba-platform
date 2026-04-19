package com.cba.loan;

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

@Tag(name = "Loan Re-amortization", description = "Recalculate the repayment schedule after partial forgiveness or modification")
@RestController
@RequestMapping("/api/v1/loans/{loanId}/reamortization")
@RequiredArgsConstructor
public class LoanReamortizationController {

    private final LoanExtensionService service;

    @Operation(summary = "List re-amortization requests for a loan")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<LoanReamortizationRequest>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listReamortization(loanId, pageable));
    }

    @Operation(summary = "Submit a re-amortization request")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanReamortizationRequest> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.ReamortizationRequest req) {
        return ApiResponse.ok(service.createReamortization(loanId, req));
    }

    @Operation(summary = "Approve a pending re-amortization request")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanReamortizationRequest> approve(
            @PathVariable UUID loanId, @PathVariable UUID id) {
        return ApiResponse.ok(service.approveReamortization(id));
    }
}
