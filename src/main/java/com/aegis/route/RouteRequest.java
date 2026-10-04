package com.aegis.route;

import com.aegis.ratelimit.RateLimitAlgorithm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RouteRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String name,
        @NotBlank @Size(max = 500) @HttpUrl String baseUrl,
        @NotNull @Min(1) @Max(120_000) Integer timeoutMs,
        @NotNull RoutePriority priority,
        Boolean enabled,
        RateLimitAlgorithm rateLimitAlgorithm,
        @Min(1) @Max(1_000_000) Integer rateLimitCapacity,
        @Min(1) @Max(86_400) Integer rateLimitWindowSeconds,
        @Min(1) @Max(1_000_000) Integer rateLimitRefillRate,
        @Min(1) @Max(10) Integer retryMaxAttempts,
        Boolean retryOnNonIdempotent,
        @Min(0) @Max(60_000) Long retryInitialBackoffMs,
        @Min(1) @Max(10) Double retryBackoffMultiplier) {

    public RouteRequest(
            String name,
            String baseUrl,
            Integer timeoutMs,
            RoutePriority priority,
            Boolean enabled,
            RateLimitAlgorithm rateLimitAlgorithm,
            Integer rateLimitCapacity,
            Integer rateLimitWindowSeconds,
            Integer rateLimitRefillRate) {
        this(name, baseUrl, timeoutMs, priority, enabled, rateLimitAlgorithm, rateLimitCapacity, rateLimitWindowSeconds, rateLimitRefillRate, null, null, null, null);
    }

    public RouteRequest(
            String name,
            String baseUrl,
            Integer timeoutMs,
            RoutePriority priority,
            Boolean enabled) {
        this(name, baseUrl, timeoutMs, priority, enabled, null, null, null, null);
    }

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
