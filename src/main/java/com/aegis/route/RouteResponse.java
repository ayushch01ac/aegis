package com.aegis.route;

import com.aegis.ratelimit.RateLimitAlgorithm;
import java.time.Instant;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        String name,
        String baseUrl,
        int timeoutMs,
        RoutePriority priority,
        boolean enabled,
        RateLimitAlgorithm rateLimitAlgorithm,
        Integer rateLimitCapacity,
        Integer rateLimitWindowSeconds,
        Integer rateLimitRefillRate,
        Instant createdAt,
        Instant updatedAt) {

    public RouteResponse(
            UUID id,
            String name,
            String baseUrl,
            int timeoutMs,
            RoutePriority priority,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
        this(id, name, baseUrl, timeoutMs, priority, enabled, null, null, null, null, createdAt, updatedAt);
    }

    static RouteResponse from(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getName(),
                route.getBaseUrl(),
                route.getTimeoutMs(),
                route.getPriority(),
                route.isEnabled(),
                route.getRateLimitAlgorithm(),
                route.getRateLimitCapacity(),
                route.getRateLimitWindowSeconds(),
                route.getRateLimitRefillRate(),
                route.getCreatedAt(),
                route.getUpdatedAt());
    }
}
