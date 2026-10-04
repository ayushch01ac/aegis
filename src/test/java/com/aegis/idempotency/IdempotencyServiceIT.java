package com.aegis.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aegis.support.AbstractPostgresIntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(properties = {
        "aegis.idempotency.enabled=true",
        "aegis.idempotency.wait-timeout-ms=1000",
        "aegis.idempotency.poll-interval-ms=5"
})
class IdempotencyServiceIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void completedResponseIsReplayedWithoutExecutingAgain() {
        AtomicInteger executions = new AtomicInteger();
        String fingerprint = IdempotencyService.fingerprint("orders", "POST", "/orders", null, new byte[] {1});

        IdempotencyResponse first = idempotencyService.execute("orders:user-a", "create-1", fingerprint,
                () -> ResponseEntity.status(HttpStatus.CREATED).header("X-Test", "one")
                        .body(new byte[] {1, 2, 3}));
        IdempotencyResponse second = idempotencyService.execute("orders:user-a", "create-1", fingerprint,
                () -> {
                    executions.incrementAndGet();
                    return ResponseEntity.ok().build();
                });

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.response().getHeaders().getFirst("X-Test")).isEqualTo("one");
        assertThat(second.response().getBody()).containsExactly(1, 2, 3);
        assertThat(executions).hasValue(0);
    }

    @Test
    void keyCannotBeReusedForADifferentRequest() {
        idempotencyService.execute("orders:user-a", "create-1", "first", () -> ResponseEntity.ok().build());

        assertThatThrownBy(() -> idempotencyService.execute(
                "orders:user-a", "create-1", "different", () -> ResponseEntity.ok().build()))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void concurrentDuplicateWaitsAndReplaysTheOriginalResponse() throws Exception {
        CountDownLatch ownerStarted = new CountDownLatch(1);
        CountDownLatch allowOwnerToFinish = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        String fingerprint = IdempotencyService.fingerprint("orders", "POST", "/orders", null, new byte[] {1});

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IdempotencyResponse> owner = executor.submit(() -> idempotencyService.execute(
                    "orders:user-a", "concurrent-1", fingerprint, () -> {
                        executions.incrementAndGet();
                        ownerStarted.countDown();
                        try {
                            if (!allowOwnerToFinish.await(1, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("test owner was not released");
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return ResponseEntity.status(HttpStatus.CREATED).body(new byte[] {9});
                    }));
            assertThat(ownerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<IdempotencyResponse> duplicate = executor.submit(() -> idempotencyService.execute(
                    "orders:user-a", "concurrent-1", fingerprint, () -> ResponseEntity.ok().build()));
            allowOwnerToFinish.countDown();

            assertThat(owner.get(1, TimeUnit.SECONDS).replayed()).isFalse();
            IdempotencyResponse replay = duplicate.get(1, TimeUnit.SECONDS);
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.response().getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(executions).hasValue(1);
        }
    }
}
