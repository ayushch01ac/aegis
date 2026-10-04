package com.aegis.idempotency;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration for Redis-backed idempotency records. */
@Validated
@ConfigurationProperties(prefix = "aegis.idempotency")
public record IdempotencyProperties(
        boolean enabled,
        @Min(1) @Max(86_400) int processingTtlSeconds,
        @Min(0) @Max(60_000) long waitTimeoutMs,
        @Min(1) @Max(1_000) long pollIntervalMs,
        @Min(1) @Max(2_592_000) int completedTtlSeconds) {
}
