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

@Tag(name = "Holidays", description = "Bank holidays — define non-working days and configure repayment scheduling rules for each")
@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HookService hookService;

    @Operation(summary = "List all bank holidays")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Holiday>> list(Pageable pageable) {
        return ApiResponse.ok(hookService.listHolidays(pageable));
    }

    @Operation(summary = "Get a bank holiday by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Holiday> get(@PathVariable UUID id) {
        return ApiResponse.ok(hookService.getHoliday(id));
    }

    @Operation(summary = "Create a bank holiday")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Holiday> create(@RequestBody HookService.CreateHolidayRequest req) {
        return ApiResponse.ok(hookService.createHoliday(req));
    }

    @Operation(summary = "Activate a pending holiday")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Holiday> activate(@PathVariable UUID id) {
        return ApiResponse.ok(hookService.activateHoliday(id));
    }

    @Operation(summary = "Delete a bank holiday")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        hookService.deleteHoliday(id);
    }
}
