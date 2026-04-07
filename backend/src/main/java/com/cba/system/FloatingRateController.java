package com.cba.system;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/floatingrates")
@RequiredArgsConstructor
public class FloatingRateController {

    private final FloatingRateService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<FloatingRate>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Pageable pageable) {
        return ApiResponse.ok(service.listRates(activeOnly, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FloatingRate> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getRate(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FloatingRate> create(@RequestBody FloatingRateService.CreateFloatingRateRequest req) {
        return ApiResponse.ok(service.createRate(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FloatingRate> update(
            @PathVariable UUID id,
            @RequestBody FloatingRateService.CreateFloatingRateRequest req) {
        return ApiResponse.ok(service.updateRate(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteRate(id);
    }
}
