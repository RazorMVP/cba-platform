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
@RequestMapping("/api/v1/loans/{loanId}/guarantors")
@RequiredArgsConstructor
public class GuarantorController {

    private final LoanExtensionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Guarantor>> list(@PathVariable UUID loanId, Pageable pageable) {
        return ApiResponse.ok(service.listGuarantors(loanId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Guarantor> create(
            @PathVariable UUID loanId,
            @RequestBody LoanExtensionService.CreateGuarantorRequest req) {
        return ApiResponse.ok(service.createGuarantor(loanId, req));
    }

    @DeleteMapping("/{guarantorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID loanId, @PathVariable UUID guarantorId) {
        service.deleteGuarantor(loanId, guarantorId);
    }
}
