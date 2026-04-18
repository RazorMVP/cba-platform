package com.cba.treasury;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Liquidity Management", description = "Real-time liquidity positions, cash flow forecasting, reserve requirements and historical snapshots")
@RestController
@RequestMapping("/api/v1/treasury/liquidity")
@RequiredArgsConstructor
public class LiquidityController {

    private final LiquidityService svc;

    // ── Live Position ───────────────────────────────────────────────────────────

    @Operation(summary = "List liquidity positions for all currencies")
    @GetMapping("/positions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquidityService.LiquidityPositionDto>>> getAllPositions() {
        return ResponseEntity.ok(ApiResponse.ok(svc.getAllPositions()));
    }

    @Operation(summary = "Get the liquidity position for a specific currency code")
    @GetMapping("/positions/{currency}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LiquidityService.LiquidityPositionDto>> getPosition(
            @PathVariable String currency) {
        return ResponseEntity.ok(ApiResponse.ok(svc.getPosition(currency)));
    }

    // ── Cash Flow Forecast ──────────────────────────────────────────────────────

    @Operation(summary = "Get a cash flow forecast for a currency over the next N days (default 30)")
    @GetMapping("/cashflow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquidityService.CashFlowEntryDto>>> getCashFlow(
            @RequestParam String currency,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(svc.getCashFlowForecast(currency, days)));
    }

    // ── Reserve Requirements CRUD ───────────────────────────────────────────────

    @Operation(summary = "List all reserve requirement rules")
    @GetMapping("/reserves")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquidityReserveRequirement>>> listReserves() {
        return ResponseEntity.ok(ApiResponse.ok(svc.listReserves()));
    }

    @Operation(summary = "Create a new reserve requirement rule")
    @PostMapping("/reserves")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<LiquidityReserveRequirement>> createReserve(
            @Valid @RequestBody LiquidityReserveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(svc.createReserve(req)));
    }

    @Operation(summary = "Update a reserve requirement rule")
    @PutMapping("/reserves/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<LiquidityReserveRequirement>> updateReserve(
            @PathVariable UUID id,
            @Valid @RequestBody LiquidityReserveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(svc.updateReserve(id, req)));
    }

    @Operation(summary = "Delete a reserve requirement rule")
    @DeleteMapping("/reserves/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReserve(@PathVariable UUID id) {
        svc.deleteReserve(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Snapshot History ────────────────────────────────────────────────────────

    @Operation(summary = "Get historical liquidity snapshots for a currency")
    @GetMapping("/snapshots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquiditySnapshot>>> getSnapshots(
            @RequestParam String currency,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(svc.getSnapshots(currency, limit)));
    }

    @Operation(summary = "Trigger an immediate liquidity snapshot for all currencies (ADMIN only)")
    @PostMapping("/snapshots/take")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> takeSnapshot() {
        svc.takeSnapshot();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
