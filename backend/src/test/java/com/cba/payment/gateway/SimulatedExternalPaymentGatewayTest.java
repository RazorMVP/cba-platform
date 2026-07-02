package com.cba.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulatedExternalPaymentGateway — default accept-all gateway")
class SimulatedExternalPaymentGatewayTest {

    private final SimulatedExternalPaymentGateway gateway = new SimulatedExternalPaymentGateway();

    private ExternalPaymentInstruction swift() {
        return new ExternalPaymentInstruction("SWIFT", new BigDecimal("100.00"), "USD",
                "Jane Doe", "GB33BUKB20201555555555", "BUKBGB22", "Barclays", "GBR", "SHA", "EXT-ABC12345");
    }

    @Test
    @DisplayName("accepts and returns a network reference tagged with the network")
    void accepts() {
        GatewayResult r = gateway.submit(swift());
        assertThat(r.accepted()).isTrue();
        assertThat(r.networkReference()).startsWith("SIM-SWIFT-");
    }

    @Test
    @DisplayName("gatewayId is SIMULATED")
    void gatewayId() {
        assertThat(gateway.gatewayId()).isEqualTo("SIMULATED");
    }
}
