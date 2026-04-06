package com.cba.notification;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class AccountEvent extends ApplicationEvent {

    public enum Type { OPENED, CLOSED, FROZEN }

    private final UUID accountId;
    private final Type type;

    public AccountEvent(Object source, UUID accountId, Type type) {
        super(source);
        this.accountId = accountId;
        this.type = type;
    }

    public UUID getAccountId() { return accountId; }
    public Type getType() { return type; }
}
