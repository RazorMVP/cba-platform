package com.cba.card;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CBA Card Management Service — Spring Boot entry point.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Card lifecycle management (issue, block, unblock, cancel, replace)</li>
 *   <li>BIN management and scheme routing</li>
 *   <li>Fraud engine (rule-based + risk scoring)</li>
 *   <li>Token vault — simulated TSP (DPAN ↔ PAN)</li>
 *   <li>Settlement (single-message real-time + dual-message batch)</li>
 *   <li>Disputes (RAISED → UNDER_REVIEW → RESOLVED)</li>
 *   <li>Terminal simulator REST API — connects to FEP TCP socket (port 8583)</li>
 * </ul>
 *
 * <p>Listens on port 8081. Called by fep-service (internal) for authorization
 * and by the Angular backoffice portal for management operations.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class CardApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardApplication.class, args);
    }
}
