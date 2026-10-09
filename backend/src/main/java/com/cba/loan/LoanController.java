package com.cba.loan;

import com.cba.common.response.ApiResponse;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.LoanRepaymentResponse;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.RepaymentScheduleResponse;
import com.cba.loan.dto.ForecloseRequest;
import com.cba.loan.dto.ForeclosureQuote;
import com.cba.loan.dto.RejectLoanRequest;
import com.cba.loan.dto.WaiveInterestRequest;
import com.cba.loan.dto.WriteOffRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
        // No JWT under the dev auth bypass: record the approver as "system" instead of failing.
        String approvedBy = jwt != null ? jwt.getClaimAsString("preferred_username") : "system";
        return ResponseEntity.ok(ApiResponse.ok(loanService.approveLoan(id, approvedBy)));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Reject a loan application (SUBMITTED, UNDER_REVIEW or APPROVED; nothing is posted)")
    public ResponseEntity<ApiResponse<LoanResponse>> rejectLoan(
            @PathVariable UUID id,
            @Valid @RequestBody RejectLoanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.rejectLoan(id, request.reason())));
    }

    @PutMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Disburse an approved loan to the linked account",
               description = "Credits the linked account and posts DR loan portfolio / CR savings control. "
                       + "400 CURRENCY_MISMATCH when the account is not in the loan product's currency.")
    public ResponseEntity<ApiResponse<LoanResponse>> disburseLoan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.disburseLoan(id)));
    }

    @GetMapping("/{id}/foreclosure-quote")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Amount needed to settle the loan on a date",
               description = "Outstanding principal, plus interest and scheduled fees on instalments due by the "
                       + "date, plus recognised unpaid charges. Interest on later instalments is not charged.")
    public ResponseEntity<ApiResponse<ForeclosureQuote>> getForeclosureQuote(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getForeclosureQuote(id, date)));
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
    @Operation(summary = "Record a loan repayment (fees → interest → principal allocation)",
               description = "paymentMethod ACCOUNT (default: linked account, or sourceAccountId) or CASH "
                       + "(tellerSessionId required; recorded in the till). Posts DR source / CR loan portfolio, "
                       + "interest receivable and fees receivable. 400 REPAYMENT_EXCEEDS_OUTSTANDING if the amount "
                       + "is more than the schedule still owes.")
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

    @PostMapping("/{id}/undo-write-off")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reverse a write-off — restores loan to IN_ARREARS with outstanding balance from schedule")
    public ResponseEntity<ApiResponse<LoanResponse>> undoWriteOff(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.undoWriteOff(id)));
    }

    @PostMapping("/{id}/waive-interest")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Waive all outstanding interest on an active loan")
    public ResponseEntity<ApiResponse<LoanResponse>> waiveInterest(
            @PathVariable UUID id,
            @Valid @RequestBody WaiveInterestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.waiveInterest(id, request)));
    }

    @PostMapping("/{id}/foreclose")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Foreclose (settle early) a loan",
               description = "Collects the foreclosure quote for foreclosureDate from paymentMethod ACCOUNT "
                       + "(default) or CASH (tellerSessionId), clears the loan's assets and cancels interest on "
                       + "instalments not yet due. Status becomes FORECLOSED.")
    public ResponseEntity<ApiResponse<LoanResponse>> forecloseLoan(
            @PathVariable UUID id,
            @Valid @RequestBody ForecloseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.forecloseLoan(id, request)));
    }
}
