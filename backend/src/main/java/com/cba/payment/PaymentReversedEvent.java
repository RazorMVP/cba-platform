package com.cba.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event published (and consumed AFTER_COMMIT) when a COMPLETED payment is reversed.
 *
 * <p>Carries the original payment's initiating actor ({@code originalCreatedBy}) so listeners
 * can attribute the reversal without {@code PaymentService} needing to know about them. In
 * particular an Open Banking listener resolves the initiating partner org from an
 * {@code "open-banking:{consentId}"} actor and fires the {@code PAYMENT.REVERSED} partner
 * webhook — keeping the {@code payment → openbanking} dependency out of the payment package.
 *
 * @param paymentId          id of the ORIGINAL payment (the one the partner was told about)
 * @param originalCreatedBy  actor that initiated the original payment (encodes the consent for PISP)
 * @param amount             reversed amount
 * @param reversalReference  reference of the reversal transaction (e.g. {@code REV-...})
 * @param reason             reversal reason
 */
public record PaymentReversedEvent(
        UUID paymentId,
        String originalCreatedBy,
        BigDecimal amount,
        String reversalReference,
        String reason) {
}
