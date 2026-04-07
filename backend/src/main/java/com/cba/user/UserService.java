package com.cba.user;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.office.Office;
import com.cba.office.OfficeRepository;
import com.cba.office.Staff;
import com.cba.office.StaffRepository;
import com.cba.user.dto.CreateUserRequest;
import com.cba.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final PlatformUserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;
    private final AuditLogService auditLogService;
    private final Keycloak keycloak;

    @Value("${keycloak.realm:cba}")
    private String realm;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw CbaException.badRequest("USERNAME_EXISTS", "Username '" + req.username() + "' is already taken");
        }

        // 1. Create in Keycloak
        String keycloakId = createKeycloakUser(req);

        // 2. Assign roles in Keycloak
        assignKeycloakRoles(keycloakId, req.roles());

        // 3. Persist locally
        PlatformUser user = new PlatformUser();
        user.setKeycloakId(keycloakId);
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setRoles(req.roles());
        user.setEnabled(true);

        if (req.officeId() != null) {
            Office office = officeRepository.findById(req.officeId())
                    .orElseThrow(() -> CbaException.notFound("Office", req.officeId().toString()));
            user.setOffice(office);
        }
        if (req.staffId() != null) {
            Staff staff = staffRepository.findById(req.staffId())
                    .orElseThrow(() -> CbaException.notFound("Staff", req.staffId().toString()));
            user.setStaff(staff);
        }

        PlatformUser saved = userRepository.save(user);
        auditLogService.log("USER", saved.getId().toString(), "CREATED", null,
                "username=" + saved.getUsername() + ",roles=" + saved.getRoles());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public void toggleUser(UUID id, boolean enable) {
        PlatformUser user = findOrThrow(id);
        user.setEnabled(enable);
        userRepository.save(user);

        // Mirror to Keycloak
        if (user.getKeycloakId() != null) {
            UserRepresentation rep = keycloak.realm(realm).users().get(user.getKeycloakId()).toRepresentation();
            rep.setEnabled(enable);
            keycloak.realm(realm).users().get(user.getKeycloakId()).update(rep);
        }
        auditLogService.log("USER", id.toString(), enable ? "ENABLED" : "DISABLED", null, null);
    }

    @Transactional
    public void deleteUser(UUID id) {
        PlatformUser user = findOrThrow(id);
        if (user.getKeycloakId() != null) {
            keycloak.realm(realm).users().delete(user.getKeycloakId());
        }
        userRepository.delete(user);
        auditLogService.log("USER", id.toString(), "DELETED", null, "username=" + user.getUsername());
    }

    // ── Keycloak helpers ──────────────────────────────────────────────────────

    private String createKeycloakUser(CreateUserRequest req) {
        UsersResource usersResource = keycloak.realm(realm).users();

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(req.password());
        cred.setTemporary(true); // force password change on first login

        UserRepresentation user = new UserRepresentation();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setEnabled(true);
        user.setCredentials(List.of(cred));

        try (Response response = usersResource.create(user)) {
            if (response.getStatus() != 201) {
                throw CbaException.badRequest("KEYCLOAK_CREATE_FAILED",
                        "Failed to create user in Keycloak: HTTP " + response.getStatus());
            }
            String location = response.getHeaderString("Location");
            return location.substring(location.lastIndexOf('/') + 1);
        }
    }

    private void assignKeycloakRoles(String keycloakId, Set<String> roles) {
        var rolesResource = keycloak.realm(realm).roles();
        List<RoleRepresentation> roleReps = roles.stream()
                .map(role -> {
                    try {
                        return rolesResource.get(role).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Role '{}' not found in Keycloak realm '{}', skipping", role, realm);
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();

        if (!roleReps.isEmpty()) {
            keycloak.realm(realm).users().get(keycloakId)
                    .roles().realmLevel().add(roleReps);
        }
    }

    private PlatformUser findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("User", id.toString()));
    }
}
