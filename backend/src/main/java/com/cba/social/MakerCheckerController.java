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

@Tag(name = "Maker-Checker", description = "Four-eyes approval workflow — queue commands for a second authoriser before execution")
@RestController
@RequestMapping("/api/v1/makercheckers")
@RequiredArgsConstructor
public class MakerCheckerController {

    private final MakerCheckerService service;

    @Operation(summary = "List pending maker-checker entries")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<MakerChecker>> listPending(Pageable pageable) {
        return ApiResponse.ok(service.listPending(pageable));
    }

    @Operation(summary = "Get a maker-checker entry by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MakerChecker> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Submit a command for maker-checker approval")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MakerChecker> create(@RequestBody MakerCheckerService.CreateMakerCheckerRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Approve or reject an entry (?command=approve|reject)")
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

    @Operation(summary = "Delete a pending maker-checker entry")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
