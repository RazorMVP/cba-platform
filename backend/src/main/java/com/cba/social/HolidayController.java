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
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HookService hookService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<Holiday>> list(Pageable pageable) {
        return ApiResponse.ok(hookService.listHolidays(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Holiday> get(@PathVariable UUID id) {
        return ApiResponse.ok(hookService.getHoliday(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Holiday> create(@RequestBody HookService.CreateHolidayRequest req) {
        return ApiResponse.ok(hookService.createHoliday(req));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Holiday> activate(@PathVariable UUID id) {
        return ApiResponse.ok(hookService.activateHoliday(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        hookService.deleteHoliday(id);
    }
}
