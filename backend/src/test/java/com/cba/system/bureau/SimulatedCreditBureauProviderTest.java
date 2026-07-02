package com.cba.system.bureau;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulatedCreditBureauProvider — deterministic default")
class SimulatedCreditBureauProviderTest {

    private final SimulatedCreditBureauProvider provider = new SimulatedCreditBureauProvider();

    @Test
    @DisplayName("same national id → same score (deterministic), always in 300..850")
    void deterministicScore() {
        CreditCheckRequest req = new CreditCheckRequest(UUID.randomUUID(), "NID-12345", "Jane Doe", "KE");
        CreditReport a = provider.pull(req);
        CreditReport b = provider.pull(req);

        assertThat(a.status()).isEqualTo(CreditReport.Status.HIT);
        assertThat(a.score()).isEqualTo(b.score());
        assertThat(a.score()).isBetween(300, 850);
        assertThat(a.band()).isEqualTo(CreditReport.bandFor(a.score()));
    }

    @Test
    @DisplayName("falls back to customer id as seed when national id is blank")
    void fallsBackToCustomerId() {
        UUID cid = UUID.randomUUID();
        CreditReport r = provider.pull(new CreditCheckRequest(cid, "  ", "No Nid", "KE"));
        assertThat(r.status()).isEqualTo(CreditReport.Status.HIT);
        assertThat(r.score()).isBetween(300, 850);
    }

    @Test
    @DisplayName("bandFor maps the FICO scale correctly")
    void bands() {
        assertThat(CreditReport.bandFor(810)).isEqualTo("EXCELLENT");
        assertThat(CreditReport.bandFor(750)).isEqualTo("VERY_GOOD");
        assertThat(CreditReport.bandFor(700)).isEqualTo("GOOD");
        assertThat(CreditReport.bandFor(600)).isEqualTo("FAIR");
        assertThat(CreditReport.bandFor(400)).isEqualTo("POOR");
    }

    @Test
    @DisplayName("providerId is SIMULATED")
    void providerId() {
        assertThat(provider.providerId()).isEqualTo("SIMULATED");
    }
}
