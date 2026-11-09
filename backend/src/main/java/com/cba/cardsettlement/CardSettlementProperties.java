package com.cba.cardsettlement;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code app.cards.settlement}: how far a scheme's reported net settlement may differ from card-service's
 * expected net and still be recorded, the difference going to GL 1004 (backlog 15d, decision D7a).
 *
 * <pre>
 * app.cards.settlement.difference-tolerance:
 *   USD: 0.05        # major units of that currency
 *   NGN: 5
 *   default: 0       # any other currency
 * </pre>
 * Keys are ISO 4217 alphabetic codes (any case) plus {@code default}. Nothing configured means 0: the
 * report must match exactly, as before. A negative value counts as 0.
 */
@Component
@ConfigurationProperties(prefix = "app.cards.settlement")
@Getter
@Setter
public class CardSettlementProperties {

    private Map<String, BigDecimal> differenceTolerance = new HashMap<>();

    /** The largest |reported − expected| recorded for {@code currency}. */
    public BigDecimal toleranceFor(String currency) {
        BigDecimal value = null;
        BigDecimal fallback = null;
        for (Map.Entry<String, BigDecimal> e : differenceTolerance.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (e.getKey().equalsIgnoreCase(currency)) value = e.getValue();
            else if (e.getKey().equalsIgnoreCase("default")) fallback = e.getValue();
        }
        BigDecimal t = value != null ? value : fallback != null ? fallback : BigDecimal.ZERO;
        return t.signum() < 0 ? BigDecimal.ZERO : t;
    }
}
