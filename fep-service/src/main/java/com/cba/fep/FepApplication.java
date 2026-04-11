package com.cba.fep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Front End Processor (FEP) application.
 *
 * <p>Provides a real ISO 8583-1987 TCP socket server for ATM, POS,
 * and terminal simulator connections. Supports five card schemes:
 * Visa, Mastercard, Verve (Interswitch), Afrigo (PAPSS), and China UnionPay.
 *
 * <p>Ports:
 * <ul>
 *   <li>8082 — Spring HTTP (health, actuator, management)</li>
 *   <li>8583 — ISO 8583 TCP socket (ATM/POS terminals)</li>
 * </ul>
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code CARD_SERVICE_URL} — card-service base URL (default: http://localhost:8081)</li>
 *   <li>{@code HSM_PROVIDER} — SOFTWARE (dev) or THALES (prod)</li>
 *   <li>{@code FEP_TCP_PORT} — override the ISO 8583 TCP port</li>
 * </ul>
 */
@SpringBootApplication
public class FepApplication {

    public static void main(String[] args) {
        SpringApplication.run(FepApplication.class, args);
    }
}
