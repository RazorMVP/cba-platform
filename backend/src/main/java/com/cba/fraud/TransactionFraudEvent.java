package com.cba.fraud;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionFraudEvent(
    UUID customerId,
    UUID accountId,
    UUID transactionId,
    BigDecimal amount,
    String currencyCode,
    String transactionType
) {}
