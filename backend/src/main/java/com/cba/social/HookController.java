package com.cba.social;

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

@Tag(name = "Hooks", description = "Web and SMS event hooks — subscribe external endpoints to platform business events")
@RestController
@RequestMapping("/api/v1/hooks")
@RequiredArgsConstructor
public class HookController {

    private final HookService hookService;

    @Operation(summary = "List all registered hooks")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<Hook>> list(Pageable pageable) {
        return ApiResponse.ok(hookService.listHooks(pageable));
    }

    @Operation(summary = "Get a hook by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Hook> get(@PathVariable UUID id) {
        return ApiResponse.ok(hookService.getHook(id));
    }

    @Operation(summary = "Register a new hook")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Hook> create(@RequestBody HookService.CreateHookRequest req) {
        return ApiResponse.ok(hookService.createHook(req));
    }

    @Operation(summary = "Update a hook")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Hook> update(@PathVariable UUID id, @RequestBody HookService.CreateHookRequest req) {
        return ApiResponse.ok(hookService.updateHook(id, req));
    }

    @Operation(summary = "Delete a hook")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        hookService.deleteHook(id);
    }
}
