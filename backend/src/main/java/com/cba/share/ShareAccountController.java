package com.cba.share;

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

@Tag(name = "Share Accounts", description = "Equity share accounts for cooperative/MFI institutions — apply, approve, activate, purchase shares, redeem and close")
@RestController
@RequestMapping("/api/v1/shareaccounts")
@RequiredArgsConstructor
public class ShareAccountController {

    private final ShareService service;

    @Operation(summary = "List share accounts, optionally filtered by ?customerId=")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ShareAccount>> list(
            @RequestParam(required = false) UUID customerId,
            Pageable pageable) {
        return ApiResponse.ok(service.listAccounts(customerId, pageable));
    }

    @Operation(summary = "Get a share account by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ShareAccount> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getAccount(id));
    }

    @Operation(summary = "Submit a new share account application")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<ShareAccount> apply(@RequestBody ShareService.ApplySharesRequest req) {
        return ApiResponse.ok(service.applyForShares(req));
    }

    @Operation(summary = "Execute a lifecycle command (?command=approve|activate|reject|close)")
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ShareAccount> command(
            @PathVariable UUID id,
            @RequestParam String command) {
        ShareAccount result = switch (command) {
            case "approve" -> service.approveAccount(id);
            case "activate" -> service.activateAccount(id);
            case "reject" -> service.rejectAccount(id);
            case "close" -> service.closeAccount(id);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        };
        return ApiResponse.ok(result);
    }

    @Operation(summary = "List share transactions for an account")
    @GetMapping("/{id}/transactions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ShareAccountTransaction>> transactions(
            @PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(service.getTransactions(id, pageable));
    }

    @Operation(summary = "Post a share transaction (?type=purchase|redeem)")
    @PostMapping("/{id}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ShareAccountTransaction> purchase(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "purchase") String type,
            @RequestBody ShareService.ShareTransactionRequest req) {
        ShareAccountTransaction result = switch (type) {
            case "purchase" -> service.purchaseShares(id, req);
            case "redeem" -> service.redeemShares(id, req);
            default -> throw new IllegalArgumentException("Unknown transaction type: " + type);
        };
        return ApiResponse.ok(result);
    }
}
