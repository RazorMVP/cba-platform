package com.cba.charge;

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

@Tag(name = "Loan Charges", description = "Charges applied to a specific loan — add, pay, waive and remove applied charge instances")
@RestController
@RequestMapping("/api/v1/loans/{loanId}/charges")
@RequiredArgsConstructor
public class LoanChargeController {

    private final ChargeService chargeService;

    @Operation(summary = "List charges applied to a loan")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<LoanCharge>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(chargeService.getLoanCharges(loanId, pageable));
    }

    @Operation(summary = "Apply a charge definition to a loan")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanCharge> add(@PathVariable UUID loanId, @RequestBody ChargeService.AddChargeRequest req) {
        return ApiResponse.ok(chargeService.addLoanCharge(loanId, req));
    }

    @Operation(summary = "Pay an outstanding charge on a loan")
    @PostMapping("/{chargeId}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanCharge> pay(@PathVariable UUID loanId, @PathVariable UUID chargeId) {
        return ApiResponse.ok(chargeService.payLoanCharge(loanId, chargeId));
    }

    @Operation(summary = "Waive an outstanding charge on a loan")
    @PostMapping("/{chargeId}/waive")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanCharge> waive(@PathVariable UUID loanId, @PathVariable UUID chargeId) {
        return ApiResponse.ok(chargeService.waiveLoanCharge(loanId, chargeId));
    }

    @Operation(summary = "Remove an applied charge from a loan")
    @DeleteMapping("/{chargeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID loanId, @PathVariable UUID chargeId) {
        chargeService.deleteLoanCharge(loanId, chargeId);
    }
}
