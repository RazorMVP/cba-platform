package com.cba.share;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shareaccounts")
@RequiredArgsConstructor
public class ShareAccountController {

    private final ShareService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ShareAccount>> list(
            @RequestParam(required = false) UUID customerId,
            Pageable pageable) {
        return ApiResponse.ok(service.listAccounts(customerId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ShareAccount> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getAccount(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<ShareAccount> apply(@RequestBody ShareService.ApplySharesRequest req) {
        return ApiResponse.ok(service.applyForShares(req));
    }

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

    @GetMapping("/{id}/transactions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ShareAccountTransaction>> transactions(
            @PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(service.getTransactions(id, pageable));
    }

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
