package com.cba.role;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<Role>> list(Pageable pageable) {
        return ApiResponse.ok(roleService.listRoles(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> get(@PathVariable UUID id) {
        return ApiResponse.ok(roleService.getRole(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> create(@RequestBody RoleService.CreateRoleRequest req) {
        return ApiResponse.ok(roleService.createRole(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> update(@PathVariable UUID id, @RequestBody RoleService.CreateRoleRequest req) {
        return ApiResponse.ok(roleService.updateRole(id, req));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Role> updatePermissions(
            @PathVariable UUID id,
            @RequestBody RoleService.UpdatePermissionsRequest req) {
        return ApiResponse.ok(roleService.updatePermissions(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Permission>> listPermissions(
            @RequestParam(required = false) String grouping) {
        return ApiResponse.ok(roleService.listPermissions(grouping));
    }
}
