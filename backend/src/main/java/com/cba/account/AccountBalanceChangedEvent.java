package com.cba.account;

import java.util.UUID;

/**
 * An account's balance changed: a {@link Transaction} row was written for it. Published inside the
 * transaction that changed the balance, so listeners that care about committed money use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record AccountBalanceChangedEvent(UUID accountId) {}
