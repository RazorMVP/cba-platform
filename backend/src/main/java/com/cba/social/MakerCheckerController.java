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
@RequestMapping("/api/v1/makercheckers")
@RequiredArgsConstructor
public class MakerCheckerController {

    private final MakerCheckerService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<MakerChecker>> listPending(Pageable pageable) {
        return ApiResponse.ok(service.listPending(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MakerChecker> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MakerChecker> create(@RequestBody MakerCheckerService.CreateMakerCheckerRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MakerChecker> command(
            @PathVariable UUID id,
            @RequestParam String command,
            @RequestParam(required = false) UUID checkerUserId) {
        MakerChecker result = switch (command) {
            case "approve" -> service.approve(id, checkerUserId);
            case "reject" -> service.reject(id, checkerUserId);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        };
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
