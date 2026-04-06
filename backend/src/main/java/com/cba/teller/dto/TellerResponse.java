package com.cba.teller.dto;

import com.cba.teller.Teller;
import com.cba.teller.TellerStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TellerResponse(
        UUID id,
        String name,
        String description,
        String branchCode,
        String officeId,
        TellerStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
    public static TellerResponse from(Teller t) {
        return new TellerResponse(t.getId(), t.getName(), t.getDescription(),
                t.getBranchCode(), t.getOfficeId(), t.getStatus(),
                t.getStartDate(), t.getEndDate());
    }
}
