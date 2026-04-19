package com.cba.deposit;

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

@Tag(name = "Recurring Deposit Accounts", description = "Recurring savings plan account lifecycle — open, approve, activate, premature close and maturity")
@RestController
@RequestMapping("/api/v1/recurringdepositaccounts")
@RequiredArgsConstructor
public class RecurringDepositAccountController {

    private final RecurringDepositService service;

    @Operation(summary = "List recurring deposit accounts (optionally filtered by ?customerId=)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<RecurringDepositAccount>> list(
            @RequestParam(required = false) UUID customerId,
            Pageable pageable) {
        return ApiResponse.ok(service.listAccounts(customerId, pageable));
    }

    @Operation(summary = "Get a recurring deposit account by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RecurringDepositAccount> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getAccount(id));
    }

    @Operation(summary = "Submit a new recurring deposit application")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<RecurringDepositAccount> submit(@RequestBody RecurringDepositService.SubmitRdRequest req) {
        return ApiResponse.ok(service.submitApplication(req));
    }

    @Operation(summary = "Execute a lifecycle command (?command=approve|activate|reject|prematureClose|mature)")
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<RecurringDepositAccount> command(
            @PathVariable UUID id,
            @RequestParam String command) {
        RecurringDepositAccount result = switch (command) {
            case "approve" -> service.approveAccount(id);
            case "activate" -> service.activateAccount(id);
            case "reject" -> service.rejectAccount(id);
            case "prematureClose" -> service.prematureClose(id);
            case "mature" -> service.matureAccount(id);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        };
        return ApiResponse.ok(result);
    }
}
