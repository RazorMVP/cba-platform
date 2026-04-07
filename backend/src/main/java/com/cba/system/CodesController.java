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
@RequestMapping("/api/v1/codes")
@RequiredArgsConstructor
public class CodesController {

    private final SystemConfigService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Code>> list(Pageable pageable) {
        return ApiResponse.ok(service.listCodes(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Code> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getCode(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Code> create(@RequestBody SystemConfigService.CreateCodeRequest req) {
        return ApiResponse.ok(service.createCode(req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteCode(id);
    }

    @GetMapping("/{id}/codevalues")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<CodeValue>> listValues(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(service.listCodeValues(id, pageable));
    }

    @PostMapping("/{id}/codevalues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CodeValue> createValue(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.CreateCodeValueRequest req) {
        return ApiResponse.ok(service.createCodeValue(id, req));
    }

    @DeleteMapping("/{id}/codevalues/{valueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteValue(@PathVariable UUID id, @PathVariable UUID valueId) {
        service.deleteCodeValue(id, valueId);
    }
}
