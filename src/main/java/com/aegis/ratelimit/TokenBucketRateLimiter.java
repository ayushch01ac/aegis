package com.aegis.ratelimit;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed rate limiter implementing the atomic token-bucket algorithm in Redis.
 *
 * <p>Tokens refill continuously over time at {@code refillRate} tokens per second up to
 * {@code capacity}. Atomic refill and token deduction are executed via Lua script.
 */
@Component
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private static final String SCRIPT_BODY = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local nowMs = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local data = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
            local tokens = tonumber(data[1])
            local lastRefillMs = tonumber(data[2])

            if tokens == nil or lastRefillMs == nil then
                tokens = capacity
                lastRefillMs = nowMs
            else
                local deltaSeconds = math.max(0, (nowMs - lastRefillMs) / 1000.0)
                tokens = math.min(capacity, tokens + (deltaSeconds * refillRate))
                lastRefillMs = nowMs
            end

            local allowed = 0
            local retryAfter = 0

            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            else
                local missing = requested - tokens
                retryAfter = math.ceil(missing / math.max(1, refillRate))
            end

            redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill_ms', tostring(lastRefillMs))
            local ttl = math.max(60, math.ceil(capacity / math.max(1, refillRate)) * 2)
            redis.call('EXPIRE', key, ttl)

            return { allowed, math.floor(tokens), retryAfter }
            """;

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;
    private final String keyPrefix;

    public TokenBucketRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${aegis.redis.key-prefix:aegis:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(SCRIPT_BODY, List.class);
        this.keyPrefix = keyPrefix;
    }

    public RateLimitResult tryAcquire(String routeKey, int capacity, int refillRatePerSecond) {
        long nowMs = Instant.now().toEpochMilli();
        String redisKey = keyPrefix + "ratelimit:tb:" + routeKey;

        List<?> response = redisTemplate.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(capacity),
                String.valueOf(refillRatePerSecond),
                String.valueOf(nowMs),
                "1");

        if (response != null && response.size() >= 3) {
            boolean allowed = ((Number) response.get(0)).longValue() == 1L;
            long remaining = ((Number) response.get(1)).longValue();
            long retryAfter = ((Number) response.get(2)).longValue();

            if (allowed) {
                log.debug("TokenBucket allow route={} remaining={}/{}", routeKey, remaining, capacity);
                return RateLimitResult.allow(capacity, remaining);
            } else {
                log.debug("TokenBucket reject route={} capacity={} retryAfter={}", routeKey, capacity, retryAfter);
                return RateLimitResult.reject(capacity, retryAfter);
            }
        }

        // Defensive fallback
        log.warn("TokenBucket Redis returned empty response for route={}", routeKey);
        return RateLimitResult.allow(capacity, capacity);
    }
}
