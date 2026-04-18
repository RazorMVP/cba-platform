package com.cba.role;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Roles & Permissions", description = "Platform roles and permission assignments — create roles, assign permission sets and manage role activation")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "List all platform roles")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<Role>> list(Pageable pageable) {
        return ApiResponse.ok(roleService.listRoles(pageable));
    }

    @Operation(summary = "Get a role by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> get(@PathVariable UUID id) {
        return ApiResponse.ok(roleService.getRole(id));
    }

    @Operation(summary = "Create a new role")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> create(@RequestBody RoleService.CreateRoleRequest req) {
        return ApiResponse.ok(roleService.createRole(req));
    }

    @Operation(summary = "Update a role's name and description")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> update(@PathVariable UUID id, @RequestBody RoleService.CreateRoleRequest req) {
        return ApiResponse.ok(roleService.updateRole(id, req));
    }

    @Operation(summary = "Replace the full permission set assigned to a role")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> updatePermissions(
            @PathVariable UUID id,
            @RequestBody RoleService.UpdatePermissionsRequest req) {
        return ApiResponse.ok(roleService.updatePermissions(id, req));
    }

    @Operation(summary = "Delete a role")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }

    @Operation(summary = "List all available permissions, optionally filtered by ?grouping=")
    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Permission>> listPermissions(
            @RequestParam(required = false) String grouping) {
        return ApiResponse.ok(roleService.listPermissions(grouping));
    }
}
