package com.cba.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a card authorization may spend from an account (card-service, debit cards). No PII.
 *
 * @param availableBalance balance − active holds − the effective floor (minimum balance, or
 *                         minus the overdraft limit), in major units of {@code currencyCode};
 *                         0 when {@code debitAllowed} is false
 * @param debitAllowed     false when the account can't be debited at all
 * @param reason           why not: ACCOUNT_NOT_ACTIVE or ACCOUNT_IN_LOCKIN_PERIOD; null when allowed
 */
public record CardFunds(UUID accountId, String currencyCode, BigDecimal availableBalance,
                        boolean debitAllowed, String reason) {}
