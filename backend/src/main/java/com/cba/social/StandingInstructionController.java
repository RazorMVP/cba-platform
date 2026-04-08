package com.cba.social;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/standinginstructions")
@RequiredArgsConstructor
public class StandingInstructionController {

    private final StandingInstructionService instructionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<StandingInstruction>> list(Pageable pageable) {
        return ApiResponse.ok(instructionService.listInstructions(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> get(@PathVariable UUID id) {
        return ApiResponse.ok(instructionService.getInstruction(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> create(
            @RequestBody StandingInstructionService.CreateInstructionRequest req) {
        return ApiResponse.ok(instructionService.createInstruction(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<StandingInstruction> update(@PathVariable UUID id,
            @RequestBody StandingInstructionService.CreateInstructionRequest req) {
        return ApiResponse.ok(instructionService.updateInstruction(id, req));
    }

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        instructionService.deleteInstruction(id);
    }
}
