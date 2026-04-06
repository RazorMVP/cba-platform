package com.cba.teller.dto;

import com.cba.teller.Cashier;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CashierResponse(
        UUID id,
        UUID tellerId,
        String staffId,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean fullDay,
        LocalTime startTime,
        LocalTime endTime,
        boolean active
) {
    public static CashierResponse from(Cashier c) {
        return new CashierResponse(c.getId(), c.getTeller().getId(), c.getStaffId(),
                c.getDescription(), c.getStartDate(), c.getEndDate(),
                c.isFullDay(), c.getStartTime(), c.getEndTime(), c.isActive());
    }
}
