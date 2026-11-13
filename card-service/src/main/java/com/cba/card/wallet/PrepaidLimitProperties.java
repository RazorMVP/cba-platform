package com.cba.card.wallet;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prepaid wallet limits per wallet currency (backlog 15c, decision D4a), bound from {@code card.prepaid.*}.
 * Keys are ISO 4217 alphabetic codes — wallets store alphabetic codes; amounts are major units:
 * <pre>
 * card:
 *   prepaid:
 *     limit-zone: Africa/Lagos        # the calendar day of the daily load limit
 *     limits:
 *       NGN:
 *         max-balance: 250000         # CBN stored-value guidelines 2012 §4.3 (§5.2 until full KYC)
 *         daily-load: 50000           # §4.2
 * </pre>
 * A currency with no entry (or an entry without a value) is not limited — for prepaid cards the issuer sets
 * the limits (§5.1). A card product's {@code max_wallet_balance} / {@code daily_wallet_load} wins over these.
 */
@Getter @Setter
@Component
@ConfigurationProperties(prefix = "card.prepaid")
public class PrepaidLimitProperties {

    /** Currency (ISO alpha) → limits. */
    private Map<String, Limit> limits = new LinkedHashMap<>();

    /** The time zone whose calendar day the daily load limit counts. */
    private String limitZone = "Africa/Lagos";

    @Getter @Setter
    public static class Limit {
        /** Most the wallet may hold after a load, major units; null = no limit. */
        private BigDecimal maxBalance;
        /** Most that may be loaded in one calendar day, major units; null = no limit. */
        private BigDecimal dailyLoad;
    }

    /** The configured limits for {@code currency} (case-insensitive), or null. */
    public Limit forCurrency(String currency) {
        if (currency == null || limits == null) return null;
        for (Map.Entry<String, Limit> e : limits.entrySet()) {
            if (e.getKey().equalsIgnoreCase(currency)) return e.getValue();
        }
        return null;
    }

    public ZoneId zone() {
        return ZoneId.of(limitZone == null || limitZone.isBlank() ? "Africa/Lagos" : limitZone);
    }
}
