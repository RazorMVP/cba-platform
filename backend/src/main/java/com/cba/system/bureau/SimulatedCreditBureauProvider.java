package com.cba.system.bureau;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default credit-bureau provider — returns a deterministic score derived from the subject's
 * national id (or customer id) without any external call. Active when
 * {@code app.creditbureau.provider} is absent or {@code SIMULATED}.
 *
 * <p>Deterministic on purpose: the same subject always yields the same score, so demos,
 * tests, and repeated loan applications are stable without a bureau contract. Scores span
 * the full FICO range 300–850.
 */
@Component
@ConditionalOnProperty(name = "app.creditbureau.provider", havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedCreditBureauProvider implements CreditBureauProvider {

    @Override
    public CreditReport pull(CreditCheckRequest request) {
        String seed = (request.nationalId() != null && !request.nationalId().isBlank())
                ? request.nationalId()
                : String.valueOf(request.customerId());
        // Map the seed's hash into 300..850 deterministically.
        int score = 300 + Math.floorMod(seed.hashCode(), 551);
        return CreditReport.hit(score, "SIM-" + Integer.toHexString(seed.hashCode()));
    }

    @Override
    public String providerId() {
        return "SIMULATED";
    }
}
