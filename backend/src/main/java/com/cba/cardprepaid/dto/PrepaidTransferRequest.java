package com.cba.cardprepaid.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A wallet load (money from {@code accountId} to the wallet) or unload (back to {@code accountId}).
 * Only the reference is validated by the framework: every other problem is a decline with a reason,
 * because card-service treats a 4xx as "unreachable" and would retry.
 *
 * @param reference    card-service's idempotency key
 * @param customerId   the card's customer; the account must be theirs
 * @param amount       major units of {@code currencyCode}
 */
public record PrepaidTransferRequest(
        @NotBlank String reference,
        UUID customerId,
        UUID cardId,
        UUID accountId,
        BigDecimal amount,
        String currencyCode) {}
