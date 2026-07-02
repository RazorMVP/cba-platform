package com.cba.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default external-payment gateway — accepts every instruction and returns a synthetic
 * network reference without contacting any bank. Active when
 * {@code app.payments.external.gateway} is absent or {@code SIMULATED}.
 *
 * <p>Keeps the platform fully usable in dev/demo (external payments settle immediately),
 * while routing through the same seam a real gateway will use — so going live is a config
 * flip, not a code change.
 */
@Component
@ConditionalOnProperty(name = "app.payments.external.gateway", havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedExternalPaymentGateway implements ExternalPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedExternalPaymentGateway.class);

    @Override
    public GatewayResult submit(ExternalPaymentInstruction instruction) {
        String networkRef = "SIM-" + instruction.network() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[EXT_PAY:SIM] accepted {} {} to {} → {}",
                instruction.network(), instruction.currencyCode(),
                instruction.beneficiaryBic(), networkRef);
        return GatewayResult.accepted(networkRef);
    }

    @Override
    public String gatewayId() {
        return "SIMULATED";
    }
}
