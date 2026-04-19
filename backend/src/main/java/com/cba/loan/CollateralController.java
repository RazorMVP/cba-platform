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

@Tag(name = "Loan Collateral", description = "Manage collateral items pledged against a loan")
@RestController
@RequestMapping("/api/v1/loans/{loanId}/collaterals")
@RequiredArgsConstructor
public class CollateralController {

    private final LoanExtensionService service;

    @Operation(summary = "List collateral items for a loan")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Collateral>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listCollaterals(loanId, pageable));
    }

    @Operation(summary = "Add a collateral item to a loan")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Collateral> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.CreateCollateralRequest req) {
        return ApiResponse.ok(service.createCollateral(loanId, req));
    }

    @Operation(summary = "Remove a collateral item from a loan")
    @DeleteMapping("/{collateralId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID loanId, @PathVariable UUID collateralId) {
        service.deleteCollateral(loanId, collateralId);
    }
}
