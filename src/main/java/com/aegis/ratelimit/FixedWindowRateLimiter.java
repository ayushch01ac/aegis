package com.aegis.ratelimit;

import java.time.Instant;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed rate limiter implementing the atomic fixed-window algorithm in Redis.
 *
 * <p>Window calculations partition time into uniform intervals of {@code windowSeconds}.
 * Redis key format: {@code {keyPrefix}ratelimit:fw:{routeKey}:{windowSeconds}:{windowBucket}}
 */
@Component
public class FixedWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);

    private static final String SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;
    private final String keyPrefix;

    public FixedWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${aegis.redis.key-prefix:aegis:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(SCRIPT, Long.class);
        this.keyPrefix = keyPrefix;
    }

    public RateLimitResult tryAcquire(String routeKey, int limit, int windowSeconds) {
        long epochSeconds = Instant.now().getEpochSecond();
        long currentWindow = epochSeconds / windowSeconds;
        String redisKey = keyPrefix + "ratelimit:fw:" + routeKey + ":" + windowSeconds + ":" + currentWindow;

        Long currentCount = redisTemplate.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(windowSeconds + 1));

        long count = currentCount != null ? currentCount : 1L;

        if (count <= limit) {
            long remaining = limit - count;
            log.debug("FixedWindow allow route={} count={}/{}", routeKey, count, limit);
            return RateLimitResult.allow(limit, remaining);
        } else {
            long nextWindowSeconds = (currentWindow + 1) * windowSeconds;
            long retryAfter = Math.max(1, nextWindowSeconds - epochSeconds);
            log.debug("FixedWindow reject route={} count={}/{} retryAfter={}", routeKey, count, limit, retryAfter);
            return RateLimitResult.reject(limit, retryAfter);
        }
    }
}
