package com.cba.treasury;

import com.cba.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/treasury/liquidity")
@RequiredArgsConstructor
public class LiquidityController {

    private final LiquidityService svc;

    // ── Live Position ───────────────────────────────────────────────────────────

    @GetMapping("/positions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquidityService.LiquidityPositionDto>>> getAllPositions() {
        return ResponseEntity.ok(ApiResponse.ok(svc.getAllPositions()));
    }

    @GetMapping("/positions/{currency}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LiquidityService.LiquidityPositionDto>> getPosition(
            @PathVariable String currency) {
        return ResponseEntity.ok(ApiResponse.ok(svc.getPosition(currency)));
    }

    // ── Cash Flow Forecast ──────────────────────────────────────────────────────

    @GetMapping("/cashflow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquidityService.CashFlowEntryDto>>> getCashFlow(
            @RequestParam String currency,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(svc.getCashFlowForecast(currency, days)));
    }

    // ── Reserve Requirements CRUD ───────────────────────────────────────────────

    @GetMapping("/reserves")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquidityReserveRequirement>>> listReserves() {
        return ResponseEntity.ok(ApiResponse.ok(svc.listReserves()));
    }

    @PostMapping("/reserves")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<LiquidityReserveRequirement>> createReserve(
            @Valid @RequestBody LiquidityReserveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(svc.createReserve(req)));
    }

    @PutMapping("/reserves/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<LiquidityReserveRequirement>> updateReserve(
            @PathVariable UUID id,
            @Valid @RequestBody LiquidityReserveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(svc.updateReserve(id, req)));
    }

    @DeleteMapping("/reserves/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReserve(@PathVariable UUID id) {
        svc.deleteReserve(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Snapshot History ────────────────────────────────────────────────────────

    @GetMapping("/snapshots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LiquiditySnapshot>>> getSnapshots(
            @RequestParam String currency,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(svc.getSnapshots(currency, limit)));
    }

    @PostMapping("/snapshots/take")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> takeSnapshot() {
        svc.takeSnapshot();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
