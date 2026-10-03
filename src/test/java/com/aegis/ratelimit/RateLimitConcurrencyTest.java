package com.aegis.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimitConcurrencyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void concurrentFixedWindowAcquisitionsRespectLimit() throws Exception {
        AtomicLong counter = new AtomicLong(0);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenAnswer(invocation -> counter.incrementAndGet());

        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(redisTemplate, "aegis:");

        int threadCount = 20;
        int limit = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<RateLimitResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    return limiter.tryAcquire("concurrent-route", limit, 60);
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        long allowedCount = 0;
        long rejectedCount = 0;

        for (Future<RateLimitResult> future : futures) {
            RateLimitResult result = future.get();
            if (result.allowed()) {
                allowedCount++;
            } else {
                rejectedCount++;
            }
        }

        assertThat(allowedCount).isEqualTo(limit);
        assertThat(rejectedCount).isEqualTo(threadCount - limit);
    }
}
