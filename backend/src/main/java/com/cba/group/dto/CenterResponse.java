package com.cba.group.dto;

import com.cba.group.Center;

import java.time.LocalDate;
import java.util.UUID;

public record CenterResponse(
        UUID id,
        String name,
        String externalId,
        Center.Status status,
        UUID officeId,
        String officeName,
        UUID staffId,
        String staffName,
        LocalDate activationDate,
        String meetingDayOfWeek
) {
    public static CenterResponse from(Center c) {
        return new CenterResponse(
                c.getId(),
                c.getName(),
                c.getExternalId(),
                c.getStatus(),
                c.getOffice() != null ? c.getOffice().getId() : null,
                c.getOffice() != null ? c.getOffice().getName() : null,
                c.getStaff() != null ? c.getStaff().getId() : null,
                c.getStaff() != null ? c.getStaff().getDisplayName() : null,
                c.getActivationDate(),
                c.getMeetingDayOfWeek()
        );
    }
}
