package com.cba.treasury;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Treasury", description = "Treasury management — fixed-term investment placements and interbank lending/borrowing positions with lifecycle commands")
@RestController
@RequestMapping("/api/v1/treasury")
@RequiredArgsConstructor
public class TreasuryController {

    private final TreasuryService service;

    // ── Placements ──────────────────────────────────────────────────────────────

    @Operation(summary = "List all treasury placements")
    @GetMapping("/placements")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TreasuryPlacement>> listPlacements() {
        return ApiResponse.ok(service.listPlacements());
    }

    @Operation(summary = "Get a treasury placement by ID")
    @GetMapping("/placements/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TreasuryPlacement> getPlacement(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPlacement(id));
    }

    @Operation(summary = "Create a new treasury placement")
    @PostMapping("/placements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryPlacement> createPlacement(@Valid @RequestBody TreasuryPlacementRequest req) {
        return ApiResponse.ok(service.createPlacement(req));
    }

    @Operation(summary = "Update a treasury placement")
    @PutMapping("/placements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryPlacement> updatePlacement(@PathVariable UUID id,
                                                           @Valid @RequestBody TreasuryPlacementRequest req) {
        return ApiResponse.ok(service.updatePlacement(id, req));
    }

    @Operation(summary = "Execute a lifecycle command on a placement (?command=activate|mature|cancel)")
    @PostMapping("/placements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryPlacement> commandPlacement(@PathVariable UUID id,
                                                            @RequestParam String command) {
        return ApiResponse.ok(service.commandPlacement(id, command));
    }

    @Operation(summary = "Delete a treasury placement")
    @DeleteMapping("/placements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deletePlacement(@PathVariable UUID id) {
        service.deletePlacement(id);
        return ApiResponse.ok(null);
    }

    // ── Interbank Positions ────────────────────────────────────────────────────

    @Operation(summary = "List all interbank positions (lending and borrowing)")
    @GetMapping("/positions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<TreasuryInterbankPosition>> listPositions() {
        return ApiResponse.ok(service.listPositions());
    }

    @Operation(summary = "Get an interbank position by ID")
    @GetMapping("/positions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TreasuryInterbankPosition> getPosition(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPosition(id));
    }

    @Operation(summary = "Create a new interbank position")
    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryInterbankPosition> createPosition(@Valid @RequestBody TreasuryInterbankRequest req) {
        return ApiResponse.ok(service.createPosition(req));
    }

    @Operation(summary = "Update an interbank position")
    @PutMapping("/positions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryInterbankPosition> updatePosition(@PathVariable UUID id,
                                                                  @Valid @RequestBody TreasuryInterbankRequest req) {
        return ApiResponse.ok(service.updatePosition(id, req));
    }

    @Operation(summary = "Execute a lifecycle command on a position (?command=settle|cancel)")
    @PostMapping("/positions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TreasuryInterbankPosition> commandPosition(@PathVariable UUID id,
                                                                   @RequestParam String command) {
        return ApiResponse.ok(service.commandPosition(id, command));
    }

    @Operation(summary = "Delete an interbank position")
    @DeleteMapping("/positions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deletePosition(@PathVariable UUID id) {
        service.deletePosition(id);
        return ApiResponse.ok(null);
    }
}
