package com.cba.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The outcome of a card hold request or release. A decline is a result, not an error.
 *
 * @param approved         true when the hold exists (placed now, or by an earlier identical request)
 * @param status           the hold's status (ACTIVE, RELEASED, EXPIRED), or null when there is no hold
 * @param reason           why not: INSUFFICIENT_FUNDS, ACCOUNT_NOT_ACTIVE, ACCOUNT_IN_LOCKIN_PERIOD,
 *                         CURRENCY_MISMATCH, HOLD_RELEASED; null when approved
 * @param availableBalance what remains spendable after this request (major units)
 */
public record CardHoldResult(boolean approved, UUID holdId, String reference, String status,
                             String reason, BigDecimal availableBalance) {}
