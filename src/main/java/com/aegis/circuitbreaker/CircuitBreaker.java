package com.aegis.circuitbreaker;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Per-route, in-memory circuit breaker for protecting downstream dependencies. */
@Component
public class CircuitBreaker {

    private final CircuitBreakerProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, RouteCircuit> circuits = new ConcurrentHashMap<>();

    public CircuitBreaker(CircuitBreakerProperties properties, Clock circuitBreakerClock) {
        this.properties = properties;
        this.clock = circuitBreakerClock;
    }

    public ResponseEntity<byte[]> execute(String routeName, Supplier<ResponseEntity<byte[]>> call) {
        if (!properties.enabled()) {
            return call.get();
        }
        RouteCircuit circuit = circuits.computeIfAbsent(routeName, ignored -> new RouteCircuit());
        circuit.beforeCall(routeName, clock.millis(), properties);
        try {
            ResponseEntity<byte[]> response = call.get();
            if (response.getStatusCode().is5xxServerError()) {
                circuit.recordFailure(clock.millis(), properties);
            } else {
                circuit.recordSuccess();
            }
            return response;
        } catch (RuntimeException exception) {
            circuit.recordFailure(clock.millis(), properties);
            throw exception;
        }
    }

    CircuitBreakerState stateOf(String routeName) {
        RouteCircuit circuit = circuits.get(routeName);
        return circuit == null ? CircuitBreakerState.CLOSED : circuit.state;
    }

    private static final class RouteCircuit {
        private CircuitBreakerState state = CircuitBreakerState.CLOSED;
        private int consecutiveFailures;
        private int halfOpenCalls;
        private long openedAtMs;

        synchronized void beforeCall(String routeName, long nowMs, CircuitBreakerProperties properties) {
            if (state == CircuitBreakerState.OPEN) {
                long elapsed = nowMs - openedAtMs;
                if (elapsed < properties.openDurationMs()) {
                    throw new CircuitOpenException(routeName,
                            (properties.openDurationMs() - elapsed + 999) / 1000);
                }
                state = CircuitBreakerState.HALF_OPEN;
                halfOpenCalls = 0;
            }
            if (state == CircuitBreakerState.HALF_OPEN) {
                if (halfOpenCalls >= properties.halfOpenMaxCalls()) {
                    throw new CircuitOpenException(routeName, 1);
                }
                halfOpenCalls++;
            }
        }

        synchronized void recordSuccess() {
            state = CircuitBreakerState.CLOSED;
            consecutiveFailures = 0;
            halfOpenCalls = 0;
        }

        synchronized void recordFailure(long nowMs, CircuitBreakerProperties properties) {
            if (state == CircuitBreakerState.HALF_OPEN) {
                open(nowMs);
                return;
            }
            if (++consecutiveFailures >= properties.failureThreshold()) {
                open(nowMs);
            }
        }

        private void open(long nowMs) {
            state = CircuitBreakerState.OPEN;
            openedAtMs = nowMs;
            consecutiveFailures = 0;
            halfOpenCalls = 0;
        }
    }
}
