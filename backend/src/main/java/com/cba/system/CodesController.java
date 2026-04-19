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

@Tag(name = "Codes & Code Values", description = "Extensible enum tables — define lookup codes and their allowed values for dropdowns across the platform")
@RestController
@RequestMapping("/api/v1/codes")
@RequiredArgsConstructor
public class CodesController {

    private final SystemConfigService service;

    @Operation(summary = "List all codes")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Code>> list(Pageable pageable) {
        return ApiResponse.ok(service.listCodes(pageable));
    }

    @Operation(summary = "Get a code by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Code> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getCode(id));
    }

    @Operation(summary = "Create a new code")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Code> create(@RequestBody SystemConfigService.CreateCodeRequest req) {
        return ApiResponse.ok(service.createCode(req));
    }

    @Operation(summary = "Delete a code (only non-system-defined codes)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteCode(id);
    }

    @Operation(summary = "List code values for a code")
    @GetMapping("/{id}/codevalues")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<CodeValue>> listValues(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(service.listCodeValues(id, pageable));
    }

    @Operation(summary = "Add a value to a code")
    @PostMapping("/{id}/codevalues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CodeValue> createValue(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.CreateCodeValueRequest req) {
        return ApiResponse.ok(service.createCodeValue(id, req));
    }

    @Operation(summary = "Remove a value from a code")
    @DeleteMapping("/{id}/codevalues/{valueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteValue(@PathVariable UUID id, @PathVariable UUID valueId) {
        service.deleteCodeValue(id, valueId);
    }
}
