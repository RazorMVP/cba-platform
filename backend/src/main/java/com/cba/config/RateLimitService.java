package com.cba.config;

import com.cba.system.GlobalConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Applies a fixed-window rate limit using an atomic Redis Lua script.
 *
 * <p>The Lua script increments a counter keyed on the caller identity and
 * sets a 60-second expiry on the first increment (one atomic operation —
 * no race condition between check and increment).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /** Requests-per-minute limits by tier name (matches global_configurations keys). */
    public enum Tier {
        SANDBOX(30), BASIC(100), PRO(500), ENTERPRISE(2000);

        private final long defaultRpm;
        Tier(long rpm) { this.defaultRpm = rpm; }
        public long defaultRpm() { return defaultRpm; }
    }

    /**
     * Atomic Lua script: INCR counter; on first increment set 60-second TTL.
     * Returns the counter value after increment.
     */
    private static final RedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], 60)
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final GlobalConfigurationRepository globalConfigRepo;

    /**
     * Checks whether {@code identity} (sub claim, client ID, or IP) has
     * exceeded the rate limit for {@code tier} on the given {@code namespace}.
     *
     * @param namespace   short prefix grouping the key (e.g. "ob", "api")
     * @param identity    unique caller identifier
     * @param tier        the caller's rate limit tier
     */
    public RateLimitResult check(String namespace, String identity, Tier tier) {
        long limit = resolveLimit(tier);
        String redisKey = "rl:" + namespace + ":" + identity;

        try {
            Long count = redis.execute(INCR_SCRIPT, List.of(redisKey));
            if (count == null) {
                return RateLimitResult.allowed(limit, limit);
            }
            long remaining = Math.max(0L, limit - count);
            return count <= limit
                    ? RateLimitResult.allowed(limit, remaining)
                    : RateLimitResult.denied(limit);
        } catch (Exception e) {
            // Redis unavailable — fail open to avoid blocking legitimate traffic
            log.warn("Rate limit Redis error (fail-open): {}", e.getMessage());
            return RateLimitResult.allowed(limit, limit);
        }
    }

    /**
     * Resolves the effective requests-per-minute limit from GlobalConfiguration.
     * Falls back to the Tier default if no DB override is present.
     */
    private long resolveLimit(Tier tier) {
        String configKey = "rate_limit_" + tier.name().toLowerCase(Locale.ROOT);
        try {
            return globalConfigRepo.findByName(configKey)
                    .map(gc -> gc.getNumericValue() != null ? gc.getNumericValue() : tier.defaultRpm())
                    .orElse(tier.defaultRpm());
        } catch (Exception e) {
            return tier.defaultRpm();
        }
    }
}
