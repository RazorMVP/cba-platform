package com.cba.group.dto;

import com.cba.group.Group;

import java.time.LocalDate;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String externalId,
        Group.Status status,
        UUID officeId,
        String officeName,
        UUID staffId,
        String staffName,
        UUID centerId,
        String centerName,
        LocalDate activationDate
) {
    public static GroupResponse from(Group g) {
        return new GroupResponse(
                g.getId(), g.getName(), g.getExternalId(), g.getStatus(),
                g.getOffice() != null ? g.getOffice().getId() : null,
                g.getOffice() != null ? g.getOffice().getName() : null,
                g.getStaff() != null ? g.getStaff().getId() : null,
                g.getStaff() != null ? g.getStaff().getDisplayName() : null,
                g.getCenter() != null ? g.getCenter().getId() : null,
                g.getCenter() != null ? g.getCenter().getName() : null,
                g.getActivationDate()
        );
    }
}
