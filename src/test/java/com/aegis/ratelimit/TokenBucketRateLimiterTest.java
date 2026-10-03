package com.aegis.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class TokenBucketRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter(redisTemplate, "aegis:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowWhenTokensAvailable() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(List.class),
                eq("10"),
                eq("2"),
                any(String.class),
                eq("1")))
                .thenReturn(List.of(1L, 9L, 0L));

        RateLimitResult result = limiter.tryAcquire("test-route", 10, 2);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(9);
        assertThat(result.retryAfterSeconds()).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectWhenTokensExhausted() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(List.class),
                eq("10"),
                eq("2"),
                any(String.class),
                eq("1")))
                .thenReturn(List.of(0L, 0L, 3L));

        RateLimitResult result = limiter.tryAcquire("test-route", 10, 2);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(0);
        assertThat(result.retryAfterSeconds()).isEqualTo(3);
    }
}
