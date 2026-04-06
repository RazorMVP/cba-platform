package com.cba.teller;

import com.cba.common.response.ApiResponse;
import com.cba.teller.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tellers")
@RequiredArgsConstructor
@SecurityRequirement(name = "oauth2")
@Tag(name = "Teller / Cash Management", description = "Teller desk, cashier allocation, and session-based cash operations")
public class TellerController {

    private final TellerService tellerService;

    // ── Teller CRUD ──────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all tellers")
    public ResponseEntity<ApiResponse<List<TellerResponse>>> getAllTellers() {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.getAllTellers()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get a teller by ID")
    public ResponseEntity<ApiResponse<TellerResponse>> getTeller(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.getTeller(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new teller desk")
    public ResponseEntity<ApiResponse<TellerResponse>> createTeller(
            @Valid @RequestBody TellerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(tellerService.createTeller(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a teller")
    public ResponseEntity<ApiResponse<TellerResponse>> updateTeller(
            @PathVariable UUID id,
            @Valid @RequestBody TellerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.updateTeller(id, request)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a teller (INACTIVE → ACTIVE)")
    public ResponseEntity<ApiResponse<TellerResponse>> activateTeller(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.activateTeller(id)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close a teller permanently (ACTIVE → CLOSED)")
    public ResponseEntity<ApiResponse<TellerResponse>> closeTeller(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.closeTeller(id)));
    }

    // ── Cashier management ───────────────────────────────────────────

    @GetMapping("/{tellerId}/cashiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List cashiers assigned to a teller")
    public ResponseEntity<ApiResponse<List<CashierResponse>>> getCashiers(@PathVariable UUID tellerId) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.getCashiers(tellerId)));
    }

    @PostMapping("/{tellerId}/cashiers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a cashier to a teller")
    public ResponseEntity<ApiResponse<CashierResponse>> assignCashier(
            @PathVariable UUID tellerId,
            @Valid @RequestBody CashierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(tellerService.assignCashier(tellerId, request)));
    }

    // ── Session lifecycle ────────────────────────────────────────────

    @GetMapping("/{tellerId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all sessions for a teller")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(@PathVariable UUID tellerId) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.getSessions(tellerId)));
    }

    @GetMapping("/{tellerId}/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get a specific session")
    public ResponseEntity<ApiResponse<SessionResponse>> getSession(
            @PathVariable UUID tellerId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.getSession(sessionId)));
    }

    @PostMapping("/{tellerId}/cashiers/{cashierId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Open a new session for a cashier (start of day with opening float)")
    public ResponseEntity<ApiResponse<SessionResponse>> openSession(
            @PathVariable UUID tellerId,
            @PathVariable UUID cashierId,
            @Valid @RequestBody OpenSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(tellerService.openSession(tellerId, cashierId, request)));
    }

    @PostMapping("/{tellerId}/sessions/{sessionId}/settle")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Close a session with cash settlement (end of day)")
    public ResponseEntity<ApiResponse<SessionResponse>> closeSession(
            @PathVariable UUID tellerId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody CloseSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.closeSession(sessionId, request)));
    }

    // ── Cash transactions ────────────────────────────────────────────

    @GetMapping("/{tellerId}/sessions/{sessionId}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all cash transactions in a session")
    public ResponseEntity<ApiResponse<List<CashTransactionResponse>>> getSessionTransactions(
            @PathVariable UUID tellerId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(tellerService.getSessionTransactions(sessionId)));
    }

    @PostMapping("/{tellerId}/sessions/{sessionId}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Record a cash-in or cash-out transaction in a session")
    public ResponseEntity<ApiResponse<CashTransactionResponse>> recordCashTransaction(
            @PathVariable UUID tellerId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody CashTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(tellerService.recordCashTransaction(sessionId, request)));
    }
}
