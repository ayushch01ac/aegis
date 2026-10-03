package com.aegis.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegis.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RedisRateLimitIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void fixedWindowRateLimiterEnforcesLimitInRedis() {
        String routeKey = "it-fw-route";
        int limit = 3;
        int windowSeconds = 60;

        RateLimitResult r1 = fixedWindowRateLimiter.tryAcquire(routeKey, limit, windowSeconds);
        RateLimitResult r2 = fixedWindowRateLimiter.tryAcquire(routeKey, limit, windowSeconds);
        RateLimitResult r3 = fixedWindowRateLimiter.tryAcquire(routeKey, limit, windowSeconds);
        RateLimitResult r4 = fixedWindowRateLimiter.tryAcquire(routeKey, limit, windowSeconds);

        assertThat(r1.allowed()).isTrue();
        assertThat(r1.remaining()).isEqualTo(2);

        assertThat(r2.allowed()).isTrue();
        assertThat(r2.remaining()).isEqualTo(1);

        assertThat(r3.allowed()).isTrue();
        assertThat(r3.remaining()).isEqualTo(0);

        assertThat(r4.allowed()).isFalse();
        assertThat(r4.remaining()).isEqualTo(0);
        assertThat(r4.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void tokenBucketRateLimiterEnforcesCapacityAndRefillInRedis() {
        String routeKey = "it-tb-route";
        int capacity = 2;
        int refillRate = 10;

        RateLimitResult r1 = tokenBucketRateLimiter.tryAcquire(routeKey, capacity, refillRate);
        RateLimitResult r2 = tokenBucketRateLimiter.tryAcquire(routeKey, capacity, refillRate);
        RateLimitResult r3 = tokenBucketRateLimiter.tryAcquire(routeKey, capacity, refillRate);

        assertThat(r1.allowed()).isTrue();
        assertThat(r1.remaining()).isEqualTo(1);

        assertThat(r2.allowed()).isTrue();
        assertThat(r2.remaining()).isEqualTo(0);

        assertThat(r3.allowed()).isFalse();
        assertThat(r3.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }
}
