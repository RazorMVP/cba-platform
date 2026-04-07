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
@RequestMapping("/api/v1/datatables")
@RequiredArgsConstructor
public class DataTableController {

    private final DataTableService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<DataTable>> list(
            @RequestParam(required = false) String applicationTableName,
            Pageable pageable) {
        return ApiResponse.ok(service.listDataTables(applicationTableName, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DataTable> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getDataTable(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DataTable> create(@RequestBody DataTableService.CreateDataTableRequest req) {
        return ApiResponse.ok(service.createDataTable(req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteDataTable(id);
    }
}
