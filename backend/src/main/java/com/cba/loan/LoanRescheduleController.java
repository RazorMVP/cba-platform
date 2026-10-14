package com.cba.loan;

import com.cba.common.exception.CbaException;
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

@Tag(name = "Loan Reschedule", description = "Request and approve changes to loan repayment terms (rate, grace periods, extra terms)")
@RestController
@RequestMapping("/api/v1/loanreschedule")
@RequiredArgsConstructor
public class LoanRescheduleController {

    private final LoanExtensionService service;

    @Operation(summary = "List reschedule requests for a loan (?loanId=)")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<LoanRescheduleRequest>> list(
            @RequestParam UUID loanId,
            Pageable pageable) {
        return ApiResponse.ok(service.listReschedules(loanId, pageable));
    }

    @Operation(summary = "Submit a loan reschedule request")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanRescheduleRequest> create(
            @RequestParam UUID loanId,
            @RequestBody LoanExtensionService.RescheduleRequest req) {
        return ApiResponse.ok(service.createReschedule(loanId, req));
    }

    @Operation(summary = "Approve or reject a reschedule request (?command=approve|reject)",
            description = "Records the decision only: the repayment schedule is not changed yet. Loan must be ACTIVE or IN_ARREARS; the request must be PENDING.")
    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanRescheduleRequest> command(
            @PathVariable UUID id,
            @RequestParam String command) {
        LoanRescheduleRequest result = switch (command) {
            case "approve" -> service.approveReschedule(id);
            case "reject"  -> service.rejectReschedule(id);
            default -> throw CbaException.badRequest("UNKNOWN_COMMAND", "Unknown command: " + command + " (approve | reject)");
        };
        return ApiResponse.ok(result);
    }
}
