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

@Tag(name = "Loan Re-aging", description = "Move overdue installments to future dates without changing the loan amount")
@RestController
@RequestMapping("/api/v1/loans/{loanId}/reaging")
@RequiredArgsConstructor
public class LoanReagingController {

    private final LoanExtensionService service;

    @Operation(summary = "List re-aging requests for a loan")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<LoanReagingRequest>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listReaging(loanId, pageable));
    }

    @Operation(summary = "Submit a re-aging request (preview=true for dry run)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanReagingRequest> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.ReagingRequest req) {
        return ApiResponse.ok(service.createReaging(loanId, req));
    }

    @Operation(summary = "Approve a pending re-aging request")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanReagingRequest> approve(@PathVariable UUID loanId, @PathVariable UUID id) {
        return ApiResponse.ok(service.approveReaging(id));
    }
}
