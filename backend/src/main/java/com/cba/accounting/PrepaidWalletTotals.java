package com.cba.accounting;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Prepaid card wallet balances per currency, from card-service (backlog 15c): the customer side of the PREPAID
 * opening balance. Empty when they can't be read. An interface so accounting doesn't depend on the card client.
 */
public interface PrepaidWalletTotals {
    Optional<Map<String, BigDecimal>> totals();
}
