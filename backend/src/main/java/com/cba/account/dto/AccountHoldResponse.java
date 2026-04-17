package com.cba.account.dto;

import com.cba.account.AccountHoldStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountHoldResponse(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String reason,
        String referenceNumber,
        AccountHoldStatus status,
        LocalDate expiryDate,
        Instant releasedAt,
        String releasedBy,
        Instant createdAt,
        String createdBy
) {}
