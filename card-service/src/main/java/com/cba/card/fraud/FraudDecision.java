package com.cba.card.fraud;

public enum FraudDecision {
    APPROVE,   // score 0–29
    STEP_UP,   // score 30–69 — force online PIN even for contactless
    DECLINE    // score 70–100
}
