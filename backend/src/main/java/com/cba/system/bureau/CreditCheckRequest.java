package com.cba.system.bureau;

import java.util.UUID;

/**
 * Inbound parameters for a credit-bureau pull. {@code nationalId} is the primary lookup
 * key at real bureaus; {@code customerId} is our own reference (also the simulated-score
 * seed when no national id is supplied).
 */
public record CreditCheckRequest(UUID customerId, String nationalId, String fullName, String country) {
}
