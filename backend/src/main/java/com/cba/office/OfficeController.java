package com.cba.office;

import com.cba.common.response.ApiResponse;
import com.cba.office.dto.OfficeRequest;
import com.cba.office.dto.OfficeResponse;
import com.cba.office.dto.StaffRequest;
import com.cba.office.dto.StaffResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Offices & Staff", description = "Branch office hierarchy and staff management")
public class OfficeController {

    private final OfficeService officeService;

    // ── Offices ──────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/offices")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a branch office")
    public ResponseEntity<ApiResponse<OfficeResponse>> createOffice(@Valid @RequestBody OfficeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.createOffice(req)));
    }

    @GetMapping("/api/v1/offices")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all active offices")
    public ResponseEntity<ApiResponse<List<OfficeResponse>>> getAllOffices() {
        return ResponseEntity.ok(ApiResponse.ok(officeService.getAllOffices()));
    }

    @GetMapping("/api/v1/offices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get a single office")
    public ResponseEntity<ApiResponse<OfficeResponse>> getOffice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.getOffice(id)));
    }

    @PutMapping("/api/v1/offices/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an office")
    public ResponseEntity<ApiResponse<OfficeResponse>> updateOffice(
            @PathVariable UUID id, @Valid @RequestBody OfficeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.updateOffice(id, req)));
    }

    // ── Staff ─────────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/staff")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a staff member")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(@Valid @RequestBody StaffRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.createStaff(req)));
    }

    @GetMapping("/api/v1/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List staff (optionally filter by officeId)")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getAllStaff(
            @RequestParam(required = false) UUID officeId) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.getAllStaff(officeId)));
    }

    @GetMapping("/api/v1/staff/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get a staff member")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaff(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.getStaff(id)));
    }

    @PutMapping("/api/v1/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a staff member")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable UUID id, @Valid @RequestBody StaffRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(officeService.updateStaff(id, req)));
    }

    @DeleteMapping("/api/v1/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a staff member")
    public ResponseEntity<ApiResponse<Void>> deactivateStaff(@PathVariable UUID id) {
        officeService.deactivateStaff(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
