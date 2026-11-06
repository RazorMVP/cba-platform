package com.cba.account;

import jakarta.persistence.PostPersist;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener on {@link Transaction}: every balance change writes a transaction row
 * (deposits, withdrawals, transfers, reversals, card movements, loan money, interest — including the
 * batch {@code saveAll} of the interest job), so this one hook sees them all and publishes
 * {@link AccountBalanceChangedEvent}. A Spring bean: Spring Boot gives Hibernate its bean container,
 * so entity listeners are created with their dependencies.
 */
@Component
@RequiredArgsConstructor
public class TransactionBalanceListener {

    private final ApplicationEventPublisher events;

    @PostPersist
    void transactionWritten(Transaction transaction) {
        if (transaction.getAccount() != null && transaction.getAccount().getId() != null) {
            events.publishEvent(new AccountBalanceChangedEvent(transaction.getAccount().getId()));
        }
    }
}
