package com.cba.loan;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans/{loanId}/reamortization")
@RequiredArgsConstructor
public class LoanReamortizationController {

    private final LoanExtensionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<LoanReamortizationRequest>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listReamortization(loanId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanReamortizationRequest> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.ReamortizationRequest req) {
        return ApiResponse.ok(service.createReamortization(loanId, req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanReamortizationRequest> approve(
            @PathVariable UUID loanId, @PathVariable UUID id) {
        return ApiResponse.ok(service.approveReamortization(id));
    }
}
