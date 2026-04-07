package com.cba.loan;

import com.cba.common.response.ApiResponse;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.LoanRepaymentResponse;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.RepaymentScheduleResponse;
import com.cba.loan.dto.WriteOffRequest;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan origination, approval, disbursement, and repayment")
@SecurityRequirement(name = "oauth2")
public class LoanController {

    private final LoanService loanService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Submit a loan application")
    public ResponseEntity<ApiResponse<LoanResponse>> applyForLoan(
            @Valid @RequestBody LoanApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(loanService.applyForLoan(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get loan details")
    public ResponseEntity<ApiResponse<LoanResponse>> getLoan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getLoan(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all loans with pagination")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> listLoans(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<LoanResponse> page = loanService.listLoans(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Approve a loan application")
    public ResponseEntity<ApiResponse<LoanResponse>> approveLoan(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String approvedBy = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(ApiResponse.ok(loanService.approveLoan(id, approvedBy)));
    }

    @PutMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Disburse an approved loan to the linked account")
    public ResponseEntity<ApiResponse<LoanResponse>> disburseLoan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.disburseLoan(id)));
    }

    @GetMapping("/{id}/repayment-schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get the full amortization / repayment schedule")
    public ResponseEntity<ApiResponse<List<RepaymentScheduleResponse>>> getRepaymentSchedule(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getRepaymentSchedule(id)));
    }

    @PostMapping("/{id}/repayments")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Record a loan repayment (fees → interest → principal allocation)")
    public ResponseEntity<ApiResponse<LoanRepaymentResponse>> makeRepayment(
            @PathVariable UUID id,
            @Valid @RequestBody LoanRepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(loanService.makeRepayment(id, request)));
    }

    @PostMapping("/{id}/write-off")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Write off an unrecoverable loan (terminal state)")
    public ResponseEntity<ApiResponse<LoanResponse>> writeOffLoan(
            @PathVariable UUID id,
            @Valid @RequestBody WriteOffRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.writeOffLoan(id, request)));
    }
}
