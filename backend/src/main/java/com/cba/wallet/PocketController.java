package com.cba.wallet;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pockets")
@RequiredArgsConstructor
@Tag(name = "Pockets", description = "Wallet sub-accounts — group savings accounts into named envelopes")
public class PocketController {

    private final PocketService pocketService;

    public record CreateRequest(
            @NotNull UUID customerId,
            @NotBlank String name,
            String description,
            List<UUID> accountIds
    ) {}

    public record UpdateRequest(String name, String description) {}

    public record LinkRequest(@NotNull List<UUID> accountIds) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "List all active pockets for a customer")
    public ResponseEntity<ApiResponse<List<PocketService.PocketResponse>>> list(
            @RequestParam UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(pocketService.listPockets(customerId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get pocket detail with linked accounts and aggregate balance")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> get(
            @PathVariable UUID id,
            @RequestParam UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(pocketService.getPocket(id, customerId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Create a pocket and optionally link savings accounts")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> create(
            @Valid @RequestBody CreateRequest req) {
        var created = pocketService.createPocket(new PocketService.CreatePocketRequest(
                req.customerId(), req.name(), req.description(), req.accountIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PostMapping("/{id}/link")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Link additional savings accounts to an existing pocket")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> linkAccounts(
            @PathVariable UUID id,
            @RequestParam UUID customerId,
            @Valid @RequestBody LinkRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(pocketService.linkAccounts(id, customerId, req.accountIds())));
    }

    @PostMapping("/{id}/delink")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Remove savings accounts from a pocket")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> delinkAccounts(
            @PathVariable UUID id,
            @RequestParam UUID customerId,
            @Valid @RequestBody LinkRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(pocketService.delinkAccounts(id, customerId, req.accountIds())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Rename or update pocket description")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> update(
            @PathVariable UUID id,
            @RequestParam UUID customerId,
            @RequestBody UpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                pocketService.updatePocket(id, customerId, new PocketService.UpdatePocketRequest(req.name(), req.description()))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Close a pocket (delinks all accounts, sets status CLOSED)")
    public ResponseEntity<ApiResponse<Void>> close(
            @PathVariable UUID id,
            @RequestParam UUID customerId) {
        pocketService.closePocket(id, customerId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
