package com.cba.notification;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class LoanEvent extends ApplicationEvent {

    public enum Type { APPLIED, APPROVED, DISBURSED, REPAYMENT_DUE, IN_ARREARS }

    private final UUID loanId;
    private final UUID customerId;
    private final Type type;

    public LoanEvent(Object source, UUID loanId, UUID customerId, Type type) {
        super(source);
        this.loanId = loanId;
        this.customerId = customerId;
        this.type = type;
    }

    public UUID getLoanId() { return loanId; }
    public UUID getCustomerId() { return customerId; }
    public Type getType() { return type; }
}
