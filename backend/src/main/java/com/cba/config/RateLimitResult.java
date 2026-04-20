package com.cba.config;

/**
 * Outcome of a single rate-limit check.
 *
 * @param allowed       true if the request is within the limit
 * @param limit         configured requests-per-minute cap for this key
 * @param remaining     requests remaining in the current window
 * @param resetSeconds  seconds until the current window resets (≈ 60)
 */
public record RateLimitResult(boolean allowed, long limit, long remaining, long resetSeconds) {

    public static RateLimitResult allowed(long limit, long remaining) {
        return new RateLimitResult(true, limit, remaining, 60L);
    }

    public static RateLimitResult denied(long limit) {
        return new RateLimitResult(false, limit, 0L, 60L);
    }
}
