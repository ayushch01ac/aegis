package com.aegis.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CircuitBreakerTest {

    @Test
    void opensAfterThresholdAndClosesAfterSuccessfulProbe() {
        MutableClock clock = new MutableClock(1_000);
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerProperties(true, 2, 100, 1), clock);

        breaker.execute("orders", () -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
        breaker.execute("orders", () -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());

        assertThat(breaker.stateOf("orders")).isEqualTo(CircuitBreakerState.OPEN);
        assertThatThrownBy(() -> breaker.execute("orders", () -> ResponseEntity.ok().build()))
                .isInstanceOf(CircuitOpenException.class);

        clock.advance(100);
        assertThat(breaker.execute("orders", () -> ResponseEntity.ok().build()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(breaker.stateOf("orders")).isEqualTo(CircuitBreakerState.CLOSED);
    }

    private static final class MutableClock extends Clock {
        private long millis;
        MutableClock(long millis) { this.millis = millis; }
        void advance(long durationMs) { millis += durationMs; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
