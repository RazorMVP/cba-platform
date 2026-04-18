package com.cba.treasury;

import com.cba.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/treasury")
@RequiredArgsConstructor
public class TreasuryController {

    private final TreasuryService service;

    // ── Placements ──────────────────────────────────────────────────────────────

    @GetMapping("/placements")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TreasuryPlacement>> listPlacements() {
        return ApiResponse.ok(service.listPlacements());
    }

    @GetMapping("/placements/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TreasuryPlacement> getPlacement(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPlacement(id));
    }

    @PostMapping("/placements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryPlacement> createPlacement(@Valid @RequestBody TreasuryPlacementRequest req) {
        return ApiResponse.ok(service.createPlacement(req));
    }

    @PutMapping("/placements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryPlacement> updatePlacement(@PathVariable UUID id,
                                                           @Valid @RequestBody TreasuryPlacementRequest req) {
        return ApiResponse.ok(service.updatePlacement(id, req));
    }

    @PostMapping("/placements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryPlacement> commandPlacement(@PathVariable UUID id,
                                                            @RequestParam String command) {
        return ApiResponse.ok(service.commandPlacement(id, command));
    }

    @DeleteMapping("/placements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deletePlacement(@PathVariable UUID id) {
        service.deletePlacement(id);
        return ApiResponse.ok(null);
    }

    // ── Interbank Positions ────────────────────────────────────────────────────

    @GetMapping("/positions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TreasuryInterbankPosition>> listPositions() {
        return ApiResponse.ok(service.listPositions());
    }

    @GetMapping("/positions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TreasuryInterbankPosition> getPosition(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPosition(id));
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryInterbankPosition> createPosition(@Valid @RequestBody TreasuryInterbankRequest req) {
        return ApiResponse.ok(service.createPosition(req));
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryInterbankPosition> updatePosition(@PathVariable UUID id,
                                                                  @Valid @RequestBody TreasuryInterbankRequest req) {
        return ApiResponse.ok(service.updatePosition(id, req));
    }

    @PostMapping("/positions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryInterbankPosition> commandPosition(@PathVariable UUID id,
                                                                   @RequestParam String command) {
        return ApiResponse.ok(service.commandPosition(id, command));
    }

    @DeleteMapping("/positions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deletePosition(@PathVariable UUID id) {
        service.deletePosition(id);
        return ApiResponse.ok(null);
    }
}
