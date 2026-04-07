package com.cba.role;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    public record CreateRoleRequest(String name, String description, List<UUID> permissionIds) {}
    public record UpdatePermissionsRequest(List<UUID> permissionIds) {}

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    // ── Roles ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Role> listRoles(Pageable p) { return roleRepository.findAll(p); }

    @Transactional(readOnly = true)
    public Role getRole(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> CbaException.notFound("Role", id));
    }

    @Transactional
    public Role createRole(CreateRoleRequest req) {
        if (roleRepository.existsByName(req.name()))
            throw CbaException.conflict("ROLE_EXISTS", "Role '" + req.name() + "' already exists");
        Role role = new Role();
        role.setName(req.name());
        role.setDescription(req.description());
        if (req.permissionIds() != null) {
            req.permissionIds().forEach(pid ->
                permissionRepository.findById(pid).ifPresent(role.getPermissions()::add));
        }
        Role saved = roleRepository.save(role);
        auditLogService.log("Role", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Role updateRole(UUID id, CreateRoleRequest req) {
        Role role = getRole(id);
        role.setName(req.name());
        role.setDescription(req.description());
        Role saved = roleRepository.save(role);
        auditLogService.log("Role", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public Role updatePermissions(UUID id, UpdatePermissionsRequest req) {
        Role role = getRole(id);
        role.getPermissions().clear();
        if (req.permissionIds() != null) {
            req.permissionIds().forEach(pid ->
                permissionRepository.findById(pid).ifPresent(role.getPermissions()::add));
        }
        Role saved = roleRepository.save(role);
        auditLogService.log("Role", id.toString(), "UPDATE_PERMISSIONS", null, saved);
        return saved;
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = getRole(id);
        roleRepository.delete(role);
        auditLogService.log("Role", id.toString(), "DELETE", null, null);
    }

    // ── Permissions ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Permission> listPermissions(String grouping) {
        if (grouping != null) return permissionRepository.findByGrouping(grouping);
        return permissionRepository.findAll();
    }
}
