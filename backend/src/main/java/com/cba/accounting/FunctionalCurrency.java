package com.cba.accounting;

import com.cba.common.exception.CbaException;
import com.cba.system.GlobalConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The ledger's single functional currency (IAS 21 §8, §17), from the global
 * configuration seeded by V54. Postings that value anything in it reject when it is
 * not configured, rather than guessing.
 */
@Component
@RequiredArgsConstructor
public class FunctionalCurrency {

    public static final String CONFIG_NAME = "functional-currency";

    private final GlobalConfigurationRepository globalConfigRepository;

    /** The ISO 4217 code, upper case; throws {@code FUNCTIONAL_CURRENCY_NOT_CONFIGURED}. */
    public String get() {
        return globalConfigRepository.findByName(CONFIG_NAME)
                .filter(c -> c.isEnabled() && c.getStringValue() != null && !c.getStringValue().isBlank())
                .map(c -> c.getStringValue().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> CbaException.badRequest("FUNCTIONAL_CURRENCY_NOT_CONFIGURED",
                        "Global configuration '" + CONFIG_NAME + "' must be set before foreign-currency postings"));
    }
}
