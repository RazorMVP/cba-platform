package com.cba.partner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records one partner API call into the day's {@code partner_usage_snapshots} row.
 *
 * <p>Runs asynchronously so metering never adds latency to the request, and uses a single
 * atomic {@code INSERT ... ON CONFLICT DO UPDATE} so concurrent requests can't lose counts.
 * The per-endpoint tally is kept in the {@code top_endpoints} JSONB object via {@code jsonb_set}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PartnerUsageRecorder {

    private final JdbcTemplate jdbc;

    private static final String UPSERT = """
            INSERT INTO partner_usage_snapshots
                (organization_id, snapshot_date, total_calls, success_calls, error_calls, top_endpoints)
            VALUES (?::uuid, CURRENT_DATE, 1, ?, ?, jsonb_build_object(?::text, 1))
            ON CONFLICT (organization_id, snapshot_date) DO UPDATE SET
                total_calls   = partner_usage_snapshots.total_calls + 1,
                success_calls = partner_usage_snapshots.success_calls + EXCLUDED.success_calls,
                error_calls   = partner_usage_snapshots.error_calls + EXCLUDED.error_calls,
                top_endpoints = jsonb_set(
                    COALESCE(partner_usage_snapshots.top_endpoints, '{}'::jsonb),
                    ARRAY[?],
                    to_jsonb(COALESCE((partner_usage_snapshots.top_endpoints ->> ?)::int, 0) + 1),
                    true)
            """;

    @Async
    public void record(UUID orgId, String endpoint, int statusCode) {
        if (orgId == null) return;
        int success = (statusCode >= 200 && statusCode < 400) ? 1 : 0;
        int error = (statusCode >= 400) ? 1 : 0;
        try {
            jdbc.update(UPSERT, orgId.toString(), success, error, endpoint, endpoint, endpoint);
        } catch (Exception e) {
            log.debug("Usage metering write failed for org={} endpoint={}: {}", orgId, endpoint, e.getMessage());
        }
    }
}
