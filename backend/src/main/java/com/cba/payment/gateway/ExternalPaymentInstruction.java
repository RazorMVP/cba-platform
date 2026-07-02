package com.cba.payment.gateway;

import java.math.BigDecimal;

/**
 * Normalised instruction handed to an {@link ExternalPaymentGateway} — the subset of an
 * external payment the network needs, decoupled from our {@code Payment} entity and DTO.
 */
public record ExternalPaymentInstruction(
        String network,              // SWIFT | SEPA | ACH
        BigDecimal amount,
        String currencyCode,
        String beneficiaryName,
        String beneficiaryIban,
        String beneficiaryBic,
        String beneficiaryBankName,
        String beneficiaryCountryCode,
        String chargeType,           // SHA | OUR | BEN
        String reference             // our internal reference (EXT-xxxx)
) {}
