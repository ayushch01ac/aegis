package com.aegis.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegis.route.Route;
import com.aegis.route.RoutePriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Mock
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    private RateLimitProperties properties;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        service = new RateLimitService(fixedWindowRateLimiter, tokenBucketRateLimiter, properties);
    }

    @Test
    void disabledRateLimiterAllowsAll() {
        properties.setEnabled(false);
        Route route = new Route("test", "http://test:8080", 1000, RoutePriority.NORMAL, true);

        RateLimitResult result = service.evaluateRateLimit(route);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void routeWithoutAlgorithmUsesDefaultFixedWindow() {
        Route route = new Route("test", "http://test:8080", 1000, RoutePriority.NORMAL, true);
        when(fixedWindowRateLimiter.tryAcquire("test", 100, 60))
                .thenReturn(RateLimitResult.allow(100, 99));

        RateLimitResult result = service.evaluateRateLimit(route);

        assertThat(result.allowed()).isTrue();
        verify(fixedWindowRateLimiter).tryAcquire("test", 100, 60);
    }

    @Test
    void routeWithExplicitTokenBucketUsesTokenBucketLimiter() {
        Route route = new Route(
                "test",
                "http://test:8080",
                1000,
                RoutePriority.NORMAL,
                true,
                RateLimitAlgorithm.TOKEN_BUCKET,
                50,
                30,
                5);

        when(tokenBucketRateLimiter.tryAcquire("test", 50, 5))
                .thenReturn(RateLimitResult.allow(50, 49));

        RateLimitResult result = service.evaluateRateLimit(route);

        assertThat(result.allowed()).isTrue();
        verify(tokenBucketRateLimiter).tryAcquire("test", 50, 5);
    }

    @Test
    void redisExceptionPropagatesWhenFailOpenDisabled() {
        properties.setFailOpen(false);
        Route route = new Route("test", "http://test:8080", 1000, RoutePriority.NORMAL, true);
        when(fixedWindowRateLimiter.tryAcquire("test", 100, 60))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThatThrownBy(() -> service.evaluateRateLimit(route))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis connection refused");
    }

    @Test
    void redisExceptionAllowsRequestWhenFailOpenEnabled() {
        properties.setFailOpen(true);
        Route route = new Route("test", "http://test:8080", 1000, RoutePriority.NORMAL, true);
        when(fixedWindowRateLimiter.tryAcquire("test", 100, 60))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RateLimitResult result = service.evaluateRateLimit(route);

        assertThat(result.allowed()).isTrue();
    }
}
