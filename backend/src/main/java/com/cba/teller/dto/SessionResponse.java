package com.cba.teller.dto;

import com.cba.teller.SessionStatus;
import com.cba.teller.TellerSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID tellerId,
        UUID cashierId,
        LocalDate sessionDate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal actualCash,
        BigDecimal difference,
        String currencyCode,
        SessionStatus status,
        String settlementNote,
        Instant openedAt,
        Instant closedAt
) {
    public static SessionResponse from(TellerSession s) {
        return new SessionResponse(
                s.getId(),
                s.getTeller().getId(),
                s.getCashier().getId(),
                s.getSessionDate(),
                s.getOpeningBalance(),
                s.getClosingBalance(),
                s.getActualCash(),
                s.getDifference(),
                s.getCurrencyCode(),
                s.getStatus(),
                s.getSettlementNote(),
                s.getOpenedAt(),
                s.getClosedAt()
        );
    }
}
