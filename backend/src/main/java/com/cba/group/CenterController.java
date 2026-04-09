package com.cba.group;

import com.cba.common.response.ApiResponse;
import com.cba.group.dto.CenterRequest;
import com.cba.group.dto.CenterResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/centers")
@RequiredArgsConstructor
@Tag(name = "Groups & Centers", description = "Microfinance group and center management, collection sheets, GLIM")
public class CenterController {

    private final CenterService centerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Create a center")
    public ResponseEntity<ApiResponse<CenterResponse>> createCenter(@Valid @RequestBody CenterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(centerService.createCenter(req)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List centers, optionally filtered by officeId")
    public ResponseEntity<ApiResponse<List<CenterResponse>>> listCenters(
            @RequestParam(required = false) UUID officeId) {
        return ResponseEntity.ok(ApiResponse.ok(centerService.listCenters(officeId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get center details")
    public ResponseEntity<ApiResponse<CenterResponse>> getCenter(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(centerService.getCenter(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Update center details")
    public ResponseEntity<ApiResponse<CenterResponse>> updateCenter(
            @PathVariable UUID id, @Valid @RequestBody CenterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(centerService.updateCenter(id, req)));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Activate a center (?command=activate)")
    public ResponseEntity<ApiResponse<CenterResponse>> commandCenter(
            @PathVariable UUID id,
            @RequestParam String command) {
        if (!"activate".equalsIgnoreCase(command)) {
            throw new IllegalArgumentException("Unknown command: " + command);
        }
        return ResponseEntity.ok(ApiResponse.ok(centerService.activateCenter(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a center")
    public void deleteCenter(@PathVariable UUID id) {
        centerService.deleteCenter(id);
    }
}
