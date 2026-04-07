package com.cba.office.dto;

import com.cba.office.Staff;

import java.time.LocalDate;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String firstName,
        String lastName,
        String displayName,
        String email,
        String mobileNo,
        LocalDate joiningDate,
        boolean loanOfficer,
        boolean active,
        UUID officeId,
        String officeName
) {
    public static StaffResponse from(Staff s) {
        return new StaffResponse(
                s.getId(), s.getFirstName(), s.getLastName(), s.getDisplayName(),
                s.getEmail(), s.getMobileNo(), s.getJoiningDate(), s.isLoanOfficer(),
                s.isActive(),
                s.getOffice() != null ? s.getOffice().getId() : null,
                s.getOffice() != null ? s.getOffice().getName() : null
        );
    }
}
