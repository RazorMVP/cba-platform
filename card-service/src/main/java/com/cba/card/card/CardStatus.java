package com.cba.card.card;

public enum CardStatus {
    // Virtual card lifecycle
    ISSUED,
    ACTIVE,
    BLOCKED,
    EXPIRED,
    CANCELLED,
    // Physical card additional states
    ORDERED,
    PRODUCED,
    DISPATCHED,
    ACTIVATION_PENDING
}
