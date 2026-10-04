package com.aegis.circuitbreaker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aegis.circuit-breaker")
public record CircuitBreakerProperties(
        boolean enabled,
        @Min(1) @Max(100) int failureThreshold,
        @Min(1) @Max(300_000) long openDurationMs,
        @Min(1) @Max(10) int halfOpenMaxCalls) {
}
