package com.aegis.ratelimit;

import com.aegis.route.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-level rate limiting service. Evaluates rate limit policies for a given {@link Route}
 * or key, using either fixed-window or token-bucket algorithms based on configuration.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final FixedWindowRateLimiter fixedWindowRateLimiter;
    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final RateLimitProperties properties;

    public RateLimitService(
            FixedWindowRateLimiter fixedWindowRateLimiter,
            TokenBucketRateLimiter tokenBucketRateLimiter,
            RateLimitProperties properties) {
        this.fixedWindowRateLimiter = fixedWindowRateLimiter;
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
        this.properties = properties;
    }

    /**
     * Evaluates rate limiting for the specified route.
     *
     * @param route the target route configuration
     * @return {@link RateLimitResult} detailing whether allowed, limits, and retry guidance
     */
    public RateLimitResult evaluateRateLimit(Route route) {
        if (!properties.isEnabled()) {
            return RateLimitResult.allow(Long.MAX_VALUE, Long.MAX_VALUE);
        }

        RateLimitAlgorithm algorithm = route.getRateLimitAlgorithm();
        if (algorithm == null) {
            algorithm = properties.getDefaultAlgorithm();
        }

        int capacity = route.getRateLimitCapacity() != null
                ? route.getRateLimitCapacity()
                : properties.getDefaultCapacity();

        int windowSeconds = route.getRateLimitWindowSeconds() != null
                ? route.getRateLimitWindowSeconds()
                : properties.getDefaultWindowSeconds();

        int refillRate = route.getRateLimitRefillRate() != null
                ? route.getRateLimitRefillRate()
                : properties.getDefaultRefillRate();

        String routeKey = route.getName();

        try {
            if (algorithm == RateLimitAlgorithm.TOKEN_BUCKET) {
                return tokenBucketRateLimiter.tryAcquire(routeKey, capacity, refillRate);
            } else {
                return fixedWindowRateLimiter.tryAcquire(routeKey, capacity, windowSeconds);
            }
        } catch (Exception ex) {
            log.error("Redis rate limiting error for route {}: {}", routeKey, ex.getMessage(), ex);
            if (properties.isFailOpen()) {
                log.warn("Fail-open enabled: allowing request despite Redis error for route {}", routeKey);
                return RateLimitResult.allow(capacity, capacity);
            } else {
                throw ex;
            }
        }
    }
}
