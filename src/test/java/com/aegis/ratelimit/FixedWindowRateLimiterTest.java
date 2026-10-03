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
class FixedWindowRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private FixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new FixedWindowRateLimiter(redisTemplate, "aegis:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowWhenCountWithinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("61")))
                .thenReturn(1L);

        RateLimitResult result = limiter.tryAcquire("test-route", 10, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(9);
        assertThat(result.retryAfterSeconds()).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectWhenCountExceedsLimit() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("61")))
                .thenReturn(11L);

        RateLimitResult result = limiter.tryAcquire("test-route", 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(0);
        assertThat(result.retryAfterSeconds()).isGreaterThan(0);
    }
}
