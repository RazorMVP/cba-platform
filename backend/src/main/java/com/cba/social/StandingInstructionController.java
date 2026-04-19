package com.cba.social;

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

import java.util.UUID;

@Tag(name = "Standing Instructions", description = "Periodic account-to-account transfer instructions — fixed amount or outstanding balance, with priority and recurrence schedule")
@RestController
@RequestMapping("/api/v1/standinginstructions")
@RequiredArgsConstructor
public class StandingInstructionController {

    private final StandingInstructionService instructionService;

    @Operation(summary = "List all standing instructions")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<StandingInstruction>> list(Pageable pageable) {
        return ApiResponse.ok(instructionService.listInstructions(pageable));
    }

    @Operation(summary = "Get a standing instruction by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> get(@PathVariable UUID id) {
        return ApiResponse.ok(instructionService.getInstruction(id));
    }

    @Operation(summary = "Create a new standing instruction")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> create(
            @RequestBody StandingInstructionService.CreateInstructionRequest req) {
        return ApiResponse.ok(instructionService.createInstruction(req));
    }

    @Operation(summary = "Update a standing instruction")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> update(@PathVariable UUID id,
            @RequestBody StandingInstructionService.CreateInstructionRequest req) {
        return ApiResponse.ok(instructionService.updateInstruction(id, req));
    }

    @Operation(summary = "Execute a command (?command=disable|enable)")
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> command(@PathVariable UUID id,
                                                     @RequestParam String command) {
        return switch (command.toLowerCase()) {
            case "disable" -> ApiResponse.ok(instructionService.disable(id));
            case "enable"  -> ApiResponse.ok(instructionService.enable(id));
            default -> throw new CbaException("UNKNOWN_COMMAND", "Unknown command: " + command,
                org.springframework.http.HttpStatus.BAD_REQUEST);
        };
    }

    @Operation(summary = "Delete a standing instruction")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        instructionService.deleteInstruction(id);
    }
}
