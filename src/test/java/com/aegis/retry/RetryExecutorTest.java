package com.aegis.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.aegis.proxy.DownstreamException;

class RetryExecutorTest {

    @Test
    void retriesIdempotentFiveHundredAndReturnsLaterSuccess() {
        RetryExecutor executor = new RetryExecutor(new RetryProperties(true, 3, 0, 2, false));
        AtomicInteger calls = new AtomicInteger();

        ResponseEntity<byte[]> response = executor.execute("orders", HttpMethod.GET,
                new RetryPolicy(3, false, 0, 2), () -> calls.incrementAndGet() == 1
                        ? ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                        : ResponseEntity.ok(new byte[0]));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryPostFiveHundred() {
        RetryExecutor executor = new RetryExecutor(new RetryProperties(true, 3, 0, 2, false));
        AtomicInteger calls = new AtomicInteger();

        ResponseEntity<byte[]> response = executor.execute("payments", HttpMethod.POST,
                new RetryPolicy(3, false, 0, 2), () -> {
                    calls.incrementAndGet();
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(calls).hasValue(1);
    }

    @Test
    void usesTheRoutePolicyBackoffRatherThanGlobalDefault() {
        assertThat(RetryExecutor.computeBackoff(25, 2, 1)).isEqualTo(25);
        assertThat(RetryExecutor.computeBackoff(25, 2, 3)).isEqualTo(100);
    }

    @Test
    void reportsAttemptCountAfterRetryableNetworkFailuresAreExhausted() {
        RetryExecutor executor = new RetryExecutor(new RetryProperties(true, 2, 0, 2, false));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.execute("orders", HttpMethod.GET,
                new RetryPolicy(2, false, 0, 2),
                () -> { throw new DownstreamException("orders", new RuntimeException("connection refused")); }))
                .isInstanceOf(RetryExhaustedException.class)
                .satisfies(exception -> assertThat(((RetryExhaustedException) exception).getHeaders()
                        .getFirst("X-Retry-Attempts")).isEqualTo("2"));
    }
}
