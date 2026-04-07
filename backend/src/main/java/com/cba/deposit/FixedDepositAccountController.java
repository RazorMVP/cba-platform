package com.cba.deposit;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fixeddepositaccounts")
@RequiredArgsConstructor
public class FixedDepositAccountController {

    private final FixedDepositService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<FixedDepositAccount>> list(
            @RequestParam(required = false) UUID customerId,
            Pageable pageable) {
        return ApiResponse.ok(service.listAccounts(customerId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FixedDepositAccount> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getAccount(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<FixedDepositAccount> submit(@RequestBody FixedDepositService.SubmitFdRequest req) {
        return ApiResponse.ok(service.submitApplication(req));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<FixedDepositAccount> command(
            @PathVariable UUID id,
            @RequestParam String command) {
        FixedDepositAccount result = switch (command) {
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
