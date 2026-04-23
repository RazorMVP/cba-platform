package com.cba.user;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.office.OfficeRepository;
import com.cba.office.StaffRepository;
import com.cba.user.dto.CreateUserRequest;
import com.cba.user.dto.UserResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — unit tests")
class UserServiceTest {

    @Mock PlatformUserRepository userRepository;
    @Mock OfficeRepository officeRepository;
    @Mock StaffRepository staffRepository;
    @Mock AuditLogService auditLogService;
    @Mock Keycloak keycloak;

    @InjectMocks UserService userService;

    private UUID userId;
    private PlatformUser user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new PlatformUser();
        user.setId(userId);
        user.setUsername("teller01");
        user.setEmail("teller01@cba.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(Set.of("TELLER"));
        user.setEnabled(true);
    }

    @Nested
    @DisplayName("Read Operations")
    class ReadOperations {

        @Test
        @DisplayName("getAllUsers returns mapped responses")
        void getAllUsers_returnsList() {
            when(userRepository.findAll()).thenReturn(List.of(user));
            List<UserResponse> result = userService.getAllUsers();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).username()).isEqualTo("teller01");
        }

        @Test
        @DisplayName("getUser returns response when found")
        void getUser_found() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            UserResponse result = userService.getUser(userId);
            assertThat(result.username()).isEqualTo("teller01");
        }

        @Test
        @DisplayName("getUser throws when not found")
        void getUser_notFound_throws() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create User")
    class CreateUser {

        @Test
        @DisplayName("createUser throws when username already exists")
        void createUser_duplicateUsername_throws() {
            when(userRepository.existsByUsername("teller01")).thenReturn(true);

            CreateUserRequest req = new CreateUserRequest(
                "teller01", "teller01@cba.com", "Password1!", "Test", "User",
                Set.of("TELLER"), null, null
            );
            assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already taken");
        }

        @Test
        @DisplayName("createUser saves user after successful Keycloak creation")
        void createUser_success() {
            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            RolesResource rolesResource = mock(RolesResource.class);
            Response response = mock(Response.class);

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(keycloak.realm(any())).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(realmResource.roles()).thenReturn(rolesResource);
            when(usersResource.create(any())).thenReturn(response);
            when(response.getStatus()).thenReturn(201);
            when(response.getHeaderString("Location"))
                .thenReturn("http://keycloak/auth/admin/realms/cba/users/abc-123");
            // roles lookup — return empty to skip role assignment
            when(rolesResource.get(anyString()))
                .thenThrow(new RuntimeException("role not found"));

            when(userRepository.save(any())).thenAnswer(inv -> {
                PlatformUser u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            CreateUserRequest req = new CreateUserRequest(
                "newuser", "new@cba.com", "Password1!", "New", "User",
                Set.of("TELLER"), null, null
            );
            UserResponse result = userService.createUser(req);
            assertThat(result.username()).isEqualTo("newuser");
        }

        @Test
        @DisplayName("createUser throws when Keycloak returns non-201")
        void createUser_keycloakFails_throws() {
            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            Response response = mock(Response.class);

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(keycloak.realm(any())).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any())).thenReturn(response);
            when(response.getStatus()).thenReturn(409);

            CreateUserRequest req = new CreateUserRequest(
                "newuser", "new@cba.com", "Password1!", "New", "User",
                Set.of("TELLER"), null, null
            );
            assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Failed to create user in Keycloak");
        }
    }

    @Nested
    @DisplayName("Enable / Disable / Delete")
    class ToggleAndDelete {

        @Test
        @DisplayName("toggleUser disables user when keycloakId is null")
        void toggleUser_noKeycloakId_disables() {
            user.setKeycloakId(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatCode(() -> userService.toggleUser(userId, false)).doesNotThrowAnyException();
            verify(userRepository).save(argThat(u -> !u.isEnabled()));
        }

        @Test
        @DisplayName("toggleUser updates Keycloak when keycloakId present")
        void toggleUser_withKeycloakId_updatesKeycloak() {
            user.setKeycloakId("kc-abc-123");
            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            UserResource userResource = mock(UserResource.class);
            UserRepresentation rep = new UserRepresentation();
            rep.setEnabled(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloak.realm(any())).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.get("kc-abc-123")).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(rep);

            assertThatCode(() -> userService.toggleUser(userId, false)).doesNotThrowAnyException();
            verify(userResource).update(any(UserRepresentation.class));
        }

        @Test
        @DisplayName("deleteUser removes user without Keycloak call when keycloakId is null")
        void deleteUser_noKeycloakId_deletesLocally() {
            user.setKeycloakId(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatCode(() -> userService.deleteUser(userId)).doesNotThrowAnyException();
            verify(userRepository).delete(user);
            verifyNoInteractions(keycloak);
        }

        @Test
        @DisplayName("deleteUser calls Keycloak delete when keycloakId present")
        void deleteUser_withKeycloakId_deletesInKeycloak() {
            user.setKeycloakId("kc-abc-123");
            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloak.realm(any())).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);

            assertThatCode(() -> userService.deleteUser(userId)).doesNotThrowAnyException();
            verify(usersResource).delete("kc-abc-123");
            verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("deleteUser throws when user not found")
        void deleteUser_notFound_throws() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(CbaException.class);
        }
    }
}
