package com.cba.user.dto;

import com.cba.user.PlatformUser;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        UUID officeId,
        String officeName,
        boolean enabled,
        Instant lastLoginAt
) {
    public static UserResponse from(PlatformUser u) {
        return new UserResponse(
                u.getId(), u.getKeycloakId(), u.getUsername(), u.getEmail(),
                u.getFirstName(), u.getLastName(), u.getRoles(),
                u.getOffice() != null ? u.getOffice().getId() : null,
                u.getOffice() != null ? u.getOffice().getName() : null,
                u.isEnabled(), u.getLastLoginAt()
        );
    }
}
