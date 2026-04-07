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
@RequestMapping("/api/v1/loanreschedule")
@RequiredArgsConstructor
public class LoanRescheduleController {

    private final LoanExtensionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<LoanRescheduleRequest>> list(
            @RequestParam UUID loanId,
            Pageable pageable) {
        return ApiResponse.ok(service.listReschedules(loanId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<LoanRescheduleRequest> create(
            @RequestParam UUID loanId,
            @RequestBody LoanExtensionService.RescheduleRequest req) {
        return ApiResponse.ok(service.createReschedule(loanId, req));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LoanRescheduleRequest> command(
            @PathVariable UUID id,
            @RequestParam String command) {
        LoanRescheduleRequest result = switch (command) {
            case "approve" -> service.approveReschedule(id);
            case "reject" -> service.rejectReschedule(id);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        };
        return ApiResponse.ok(result);
    }
}
