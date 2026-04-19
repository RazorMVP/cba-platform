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

@Tag(name = "DataTables", description = "Dynamic schema extension tables — register custom columns against any core entity type")
@RestController
@RequestMapping("/api/v1/datatables")
@RequiredArgsConstructor
public class DataTableController {

    private final DataTableService service;

    @Operation(summary = "List registered data tables (optionally filtered by ?applicationTableName=)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<DataTable>> list(
            @RequestParam(required = false) String applicationTableName,
            Pageable pageable) {
        return ApiResponse.ok(service.listDataTables(applicationTableName, pageable));
    }

    @Operation(summary = "Get a data table by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DataTable> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getDataTable(id));
    }

    @Operation(summary = "Register a new data table")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DataTable> create(@RequestBody DataTableService.CreateDataTableRequest req) {
        return ApiResponse.ok(service.createDataTable(req));
    }

    @Operation(summary = "Deregister a data table")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteDataTable(id);
    }
}
