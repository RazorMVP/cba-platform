package com.cba.card.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Per-API-key rate limiting backed by Redis fixed-window counters.
 *
 * <p>Tier limits (requests/minute):
 * <ul>
 *   <li>SANDBOX — 30 (for {@code sk_test_} keys)
 *   <li>BASIC   — 100 (default for all production keys)
 *   <li>PRO     — 500
 *   <li>ENTERPRISE — 2000
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    public enum Tier {
        SANDBOX(30), BASIC(100), PRO(500), ENTERPRISE(2000);

        private final long rpm;
        Tier(long rpm) { this.rpm = rpm; }
        public long rpm() { return rpm; }

        public static Tier fromString(String value) {
            if (value == null) return BASIC;
            try { return valueOf(value.toUpperCase()); }
            catch (IllegalArgumentException e) { return BASIC; }
        }
    }

    private static final RedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], 60)
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * Counts one request against {@code identity}'s bucket ({@code rl:card:<identity>}) at
     * {@code tier}. The identity comes from the verified caller — see {@link RateLimitFilter}.
     */
    public RateLimitResult check(String identity, Tier tier) {
        try {
            Long count = redis.execute(INCR_SCRIPT, List.of("rl:card:" + identity));
            if (count == null) return RateLimitResult.allowed(tier.rpm(), tier.rpm());
            long remaining = Math.max(0L, tier.rpm() - count);
            return count <= tier.rpm()
                    ? RateLimitResult.allowed(tier.rpm(), remaining)
                    : RateLimitResult.denied(tier.rpm());
        } catch (Exception e) {
            log.warn("Rate limit Redis error (fail-open): {}", e.getMessage());
            return RateLimitResult.allowed(tier.rpm(), tier.rpm());
        }
    }
}
