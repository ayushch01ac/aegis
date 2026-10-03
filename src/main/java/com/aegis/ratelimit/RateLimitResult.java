package com.aegis.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds) {

    public static RateLimitResult allow(long limit, long remaining) {
        return new RateLimitResult(true, limit, Math.max(0, remaining), 0);
    }

    public static RateLimitResult reject(long limit, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, Math.max(1, retryAfterSeconds));
    }
}
