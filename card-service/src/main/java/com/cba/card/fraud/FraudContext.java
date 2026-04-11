package com.cba.card.fraud;

import com.cba.card.card.Card;
import com.cba.card.card.CardStatus;
import com.cba.card.card.CardType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Immutable context object passed to each fraud rule evaluator.
 * Built by FraudEngine from the incoming authorization request.
 */
public record FraudContext(
        Card               card,
        String             pan,
        BigDecimal         amount,        // in minor units (cents)
        String             currencyCode,
        String             processingCode,
        String             terminalId,
        String             merchantId,
        String             merchantName,
        String             mcc,
        String             posEntryMode,  // SWIPE, CHIP, CONTACTLESS, CNP
        String             stan,
        boolean            pinVerified,
        boolean            arqcValid,
        boolean            isFinancial,
        Map<String,Object> ruleParams     // rule-specific config from DB
) {
    /** True when the card is expired based on YYMM in expiryDate field. */
    public boolean isCardExpired() {
        if (card == null) return false;
        String exp = card.getExpiryDate(); // YYMM
        String now = java.time.YearMonth.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMM"));
        return exp.compareTo(now) < 0;
    }

    /** True when card status is BLOCKED or CANCELLED. */
    public boolean isCardBlocked() {
        return card != null &&
               (card.getStatus() == CardStatus.BLOCKED || card.getStatus() == CardStatus.CANCELLED);
    }

    /** True for card-not-present entry modes. */
    public boolean isCardNotPresent() {
        return "CNP".equalsIgnoreCase(posEntryMode) || "010".equals(posEntryMode);
    }

    public boolean isContactless() {
        return "CONTACTLESS".equalsIgnoreCase(posEntryMode) || "071".equals(posEntryMode);
    }

    public CardType cardType() {
        return card != null ? card.getCardType() : null;
    }
}
