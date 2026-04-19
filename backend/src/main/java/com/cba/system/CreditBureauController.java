package com.cba.system;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Credit Bureaus", description = "Credit bureau integration configuration and loan product mappings — manage external credit check adapters")
@RestController
@RequestMapping("/api/v1/creditbureaus")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;

    @Operation(summary = "List all credit bureau integrations")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<CreditBureauIntegration>> list(Pageable pageable) {
        return ApiResponse.ok(creditBureauService.listIntegrations(pageable));
    }

    @Operation(summary = "Get a credit bureau integration by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditBureauIntegration> get(@PathVariable UUID id) {
        return ApiResponse.ok(creditBureauService.getIntegration(id));
    }

    @Operation(summary = "Create a new credit bureau integration")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditBureauIntegration> create(
            @RequestBody CreditBureauService.CreateIntegrationRequest req) {
        return ApiResponse.ok(creditBureauService.createIntegration(req));
    }

    @Operation(summary = "Update a credit bureau integration")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditBureauIntegration> update(@PathVariable UUID id,
            @RequestBody CreditBureauService.CreateIntegrationRequest req) {
        return ApiResponse.ok(creditBureauService.updateIntegration(id, req));
    }

    @Operation(summary = "Execute a command (?command=activate|deactivate)")
    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditBureauIntegration> command(@PathVariable UUID id,
                                                         @RequestParam String command) {
        return switch (command.toLowerCase()) {
            case "activate"   -> ApiResponse.ok(creditBureauService.activate(id));
            case "deactivate" -> ApiResponse.ok(creditBureauService.deactivate(id));
            default -> throw new CbaException("UNKNOWN_COMMAND", "Unknown command: " + command,
                org.springframework.http.HttpStatus.BAD_REQUEST);
        };
    }

    @Operation(summary = "Delete a credit bureau integration")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        creditBureauService.deleteIntegration(id);
    }

    @Operation(summary = "List loan product mappings for a bureau")
    @GetMapping("/{id}/mappings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<CreditBureauProductMapping>> listMappings(@PathVariable UUID id) {
        return ApiResponse.ok(creditBureauService.listMappings(id));
    }

    @Operation(summary = "Map a loan product to this bureau")
    @PostMapping("/{id}/mappings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditBureauProductMapping> createMapping(@PathVariable UUID id,
            @RequestBody CreditBureauService.CreateMappingRequest req) {
        return ApiResponse.ok(creditBureauService.createMapping(id, req));
    }

    @Operation(summary = "Remove a loan product mapping from a bureau")
    @DeleteMapping("/{id}/mappings/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteMapping(@PathVariable UUID id, @PathVariable UUID mappingId) {
        creditBureauService.deleteMapping(mappingId);
    }
}
