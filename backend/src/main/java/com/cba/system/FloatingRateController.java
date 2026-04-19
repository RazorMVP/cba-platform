package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Floating Rates", description = "Variable interest rate curves with dated periods — base lending rates and differential rates used by loan products")
@RestController
@RequestMapping("/api/v1/floatingrates")
@RequiredArgsConstructor
public class FloatingRateController {

    private final FloatingRateService service;

    @Operation(summary = "List floating rates (?activeOnly=true to filter inactive)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<FloatingRate>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Pageable pageable) {
        return ApiResponse.ok(service.listRates(activeOnly, pageable));
    }

    @Operation(summary = "Get a floating rate by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FloatingRate> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getRate(id));
    }

    @Operation(summary = "Create a new floating rate with rate periods")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FloatingRate> create(@RequestBody FloatingRateService.CreateFloatingRateRequest req) {
        return ApiResponse.ok(service.createRate(req));
    }

    @Operation(summary = "Update a floating rate and replace all its rate periods")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FloatingRate> update(
            @PathVariable UUID id,
            @RequestBody FloatingRateService.CreateFloatingRateRequest req) {
        return ApiResponse.ok(service.updateRate(id, req));
    }

    @Operation(summary = "Delete a floating rate")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteRate(id);
    }
}
