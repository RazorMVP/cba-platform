package com.cba.charge;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans/{loanId}/charges")
@RequiredArgsConstructor
public class LoanChargeController {

    private final ChargeService chargeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<LoanCharge>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(chargeService.getLoanCharges(loanId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanCharge> add(@PathVariable UUID loanId, @RequestBody ChargeService.AddChargeRequest req) {
        return ApiResponse.ok(chargeService.addLoanCharge(loanId, req));
    }

    @PostMapping("/{chargeId}/waive")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanCharge> waive(@PathVariable UUID loanId, @PathVariable UUID chargeId) {
        return ApiResponse.ok(chargeService.waiveLoanCharge(loanId, chargeId));
    }

    @DeleteMapping("/{chargeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID loanId, @PathVariable UUID chargeId) {
        chargeService.deleteLoanCharge(loanId, chargeId);
    }
}
