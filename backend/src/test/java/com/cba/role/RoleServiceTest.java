package com.cba.role;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService — unit tests")
class RoleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks RoleService service;

    private UUID roleId;
    private Role role;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
        role = new Role();
        role.setId(roleId);
        role.setName("TELLER");
        role.setDescription("Bank teller role");
        role.setPermissions(new ArrayList<>());
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listRoles returns page")
        void listRoles_returnsPage() {
            when(roleRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(role)));
            assertThat(service.listRoles(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getRole returns role when found")
        void getRole_found() {
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
            assertThat(service.getRole(roleId).getName()).isEqualTo("TELLER");
        }

        @Test
        @DisplayName("getRole throws when not found")
        void getRole_notFound_throws() {
            when(roleRepository.findById(roleId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getRole(roleId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createRole saves role when name is unique")
        void createRole_success() {
            when(roleRepository.existsByName("TELLER")).thenReturn(false);
            when(roleRepository.save(any())).thenReturn(role);

            RoleService.CreateRoleRequest req =
                new RoleService.CreateRoleRequest("TELLER", "Bank teller", null);
            Role result = service.createRole(req);
            assertThat(result.getName()).isEqualTo("TELLER");
        }

        @Test
        @DisplayName("createRole throws when name already exists")
        void createRole_duplicateName_throws() {
            when(roleRepository.existsByName("TELLER")).thenReturn(true);

            RoleService.CreateRoleRequest req =
                new RoleService.CreateRoleRequest("TELLER", "desc", null);
            assertThatThrownBy(() -> service.createRole(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("createRole adds permissions when permissionIds provided")
        void createRole_withPermissions() {
            UUID permId = UUID.randomUUID();
            Permission perm = new Permission();
            perm.setId(permId);

            when(roleRepository.existsByName("ADMIN")).thenReturn(false);
            when(permissionRepository.findById(permId)).thenReturn(Optional.of(perm));
            when(roleRepository.save(any())).thenReturn(role);

            RoleService.CreateRoleRequest req =
                new RoleService.CreateRoleRequest("ADMIN", "Admin role", List.of(permId));
            service.createRole(req);
            verify(permissionRepository).findById(permId);
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("updateRole saves changes")
        void updateRole_success() {
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
            when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RoleService.CreateRoleRequest req =
                new RoleService.CreateRoleRequest("UPDATED_TELLER", "Updated desc", null);
            Role result = service.updateRole(roleId, req);
            assertThat(result.getName()).isEqualTo("UPDATED_TELLER");
        }

        @Test
        @DisplayName("updatePermissions clears and re-adds permissions")
        void updatePermissions_success() {
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
            when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RoleService.UpdatePermissionsRequest req =
                new RoleService.UpdatePermissionsRequest(List.of());
            Role result = service.updatePermissions(roleId, req);
            assertThat(result.getPermissions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("deleteRole removes role")
        void deleteRole_success() {
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

            assertThatCode(() -> service.deleteRole(roleId)).doesNotThrowAnyException();
            verify(roleRepository).delete(role);
        }
    }

    @Nested
    @DisplayName("Permissions")
    class Permissions {

        @Test
        @DisplayName("listPermissions with grouping calls findByGrouping")
        void listPermissions_withGrouping() {
            when(permissionRepository.findByGrouping("LOAN")).thenReturn(List.of());
            List<Permission> result = service.listPermissions("LOAN");
            assertThat(result).isEmpty();
            verify(permissionRepository).findByGrouping("LOAN");
        }

        @Test
        @DisplayName("listPermissions without grouping calls findAll")
        void listPermissions_noGrouping() {
            when(permissionRepository.findAll()).thenReturn(List.of());
            List<Permission> result = service.listPermissions(null);
            assertThat(result).isEmpty();
            verify(permissionRepository).findAll();
        }
    }
}
