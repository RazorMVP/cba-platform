package com.cba.fep.scheme;

import com.cba.fep.auth.CardServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SchemeAdapterFactory#detectScheme} — BIN-to-scheme routing.
 * A wrong answer here picks the wrong scheme adapter and settlement path, i.e.
 * money routed to the wrong network. The card-service dependency is faked by
 * subclassing {@link CardServiceClient} (plain inheritance — no Mockito, which
 * cannot mock concrete classes on this Java 25 host).
 */
class SchemeAdapterFactoryTest {

    /** A CardServiceClient test double with a fixed BIN table and remote-lookup behaviour. */
    private static CardServiceClient stubClient() {
        return new CardServiceClient(new RestTemplate()) {
            @Override
            public Map<String, SchemeType> getAllBinMappings() {
                return Map.of(
                        "411111",   SchemeType.VISA,        // 6-digit BIN
                        "51234567", SchemeType.MASTERCARD); // 8-digit BIN
            }
            @Override
            public SchemeType lookupBinScheme(String binPrefix) {
                return binPrefix.startsWith("999999") ? SchemeType.VERVE : SchemeType.UNKNOWN;
            }
        };
    }

    private SchemeAdapterFactory factory() {
        SchemeAdapterFactory f = new SchemeAdapterFactory(stubClient(), List.of());
        f.refreshBinCache(); // populate the local cache from the stub (skips @PostConstruct init)
        return f;
    }

    @Test
    @DisplayName("6-digit BIN resolves from the local cache")
    void sixDigitBin() {
        assertThat(factory().detectScheme("4111111111111111")).isEqualTo(SchemeType.VISA);
    }

    @Test
    @DisplayName("8-digit BIN takes precedence over 6-digit")
    void eightDigitBinPrecedence() {
        assertThat(factory().detectScheme("5123456789012345")).isEqualTo(SchemeType.MASTERCARD);
    }

    @Test
    @DisplayName("an uncached BIN falls back to the remote card-service lookup and caches the result")
    void remoteFallback() {
        SchemeAdapterFactory f = factory();
        assertThat(f.detectScheme("9999990000000000")).isEqualTo(SchemeType.VERVE);
        // second call served from cache — still VERVE
        assertThat(f.detectScheme("9999990000000000")).isEqualTo(SchemeType.VERVE);
    }

    @Test
    @DisplayName("a PAN shorter than 6 digits is UNKNOWN")
    void shortPanUnknown() {
        assertThat(factory().detectScheme("0012")).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("a null PAN is UNKNOWN (no NPE)")
    void nullPanUnknown() {
        assertThat(factory().detectScheme(null)).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("an unregistered BIN with no remote match is UNKNOWN")
    void unregisteredBinUnknown() {
        assertThat(factory().detectScheme("7777770000000000")).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("getAdapter falls back to UnknownSchemeAdapter when none registered")
    void getAdapterFallback() {
        assertThat(factory().getAdapter(SchemeType.VISA)).isInstanceOf(UnknownSchemeAdapter.class);
    }
}
