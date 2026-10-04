package com.aegis.retry;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Global defaults for the retry policy, bound from {@code aegis.retry.*}.
 *
 * <p>Individual routes may override {@link #defaultMaxAttempts()},
 * {@link #defaultInitialBackoffMs()}, and {@link #defaultBackoffMultiplier()}
 * through their own stored configuration. These values apply when a route has
 * no per-route overrides and {@link #enabled()} is {@code true}.
 *
 * <p>Design note: {@link #defaultRetryOnNonIdempotent()} is {@code false} by default
 * because retrying POST/PATCH/DELETE requests on network-level failures risks
 * unintended duplicate side-effects. An operator must opt routes in explicitly.
 */
@Validated
@ConfigurationProperties(prefix = "aegis.retry")
public record RetryProperties(
        boolean enabled,
        @Min(1) @Max(10) int defaultMaxAttempts,
        @Min(0) @Max(60_000) long defaultInitialBackoffMs,
        @Min(1) @Max(10) double defaultBackoffMultiplier,
        boolean defaultRetryOnNonIdempotent) {

    public RetryProperties {
        // compact constructor — Bean Validation enforces bounds.
    }
}
