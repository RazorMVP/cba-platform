package com.cba.social;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hooks")
@RequiredArgsConstructor
public class HookController {

    private final HookService hookService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<Hook>> list(Pageable pageable) {
        return ApiResponse.ok(hookService.listHooks(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Hook> get(@PathVariable UUID id) {
        return ApiResponse.ok(hookService.getHook(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Hook> create(@RequestBody HookService.CreateHookRequest req) {
        return ApiResponse.ok(hookService.createHook(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Hook> update(@PathVariable UUID id, @RequestBody HookService.CreateHookRequest req) {
        return ApiResponse.ok(hookService.updateHook(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        hookService.deleteHook(id);
    }
}
