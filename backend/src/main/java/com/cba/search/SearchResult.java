package com.cba.search;

import java.util.UUID;

/**
 * A single search hit returned by the global search endpoint.
 */
public record SearchResult(
    UUID   entityId,
    String entityType,    // CLIENT | GROUP | LOAN | SAVINGS
    String entityName,
    String entityAccountNo,
    String entityStatus,
    String entityExternalId
) {}
