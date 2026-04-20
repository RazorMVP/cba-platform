package com.cba.card.config;

public record RateLimitResult(boolean allowed, long limit, long remaining) {

    public static RateLimitResult allowed(long limit, long remaining) {
        return new RateLimitResult(true, limit, remaining);
    }

    public static RateLimitResult denied(long limit) {
        return new RateLimitResult(false, limit, 0L);
    }
}
