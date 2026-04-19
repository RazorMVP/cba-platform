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

@Tag(name = "Loan Guarantors", description = "Manage guarantors associated with a loan")
@RestController
@RequestMapping("/api/v1/loans/{loanId}/guarantors")
@RequiredArgsConstructor
public class GuarantorController {

    private final LoanExtensionService service;

    @Operation(summary = "List guarantors for a loan")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Guarantor>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listGuarantors(loanId, pageable));
    }

    @Operation(summary = "Add a guarantor to a loan")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Guarantor> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.CreateGuarantorRequest req) {
        return ApiResponse.ok(service.createGuarantor(loanId, req));
    }

    @Operation(summary = "Remove a guarantor from a loan")
    @DeleteMapping("/{guarantorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID loanId, @PathVariable UUID guarantorId) {
        service.deleteGuarantor(loanId, guarantorId);
    }
}
