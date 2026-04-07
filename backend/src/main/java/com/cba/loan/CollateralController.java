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
@RequestMapping("/api/v1/loans/{loanId}/collaterals")
@RequiredArgsConstructor
public class CollateralController {

    private final LoanExtensionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Collateral>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listCollaterals(loanId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Collateral> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.CreateCollateralRequest req) {
        return ApiResponse.ok(service.createCollateral(loanId, req));
    }

    @DeleteMapping("/{collateralId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID loanId, @PathVariable UUID collateralId) {
        service.deleteCollateral(loanId, collateralId);
    }
}
