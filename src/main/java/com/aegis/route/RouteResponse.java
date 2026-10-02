package com.aegis.route;

import java.time.Instant;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        String name,
        String baseUrl,
        int timeoutMs,
        RoutePriority priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    static RouteResponse from(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getName(),
                route.getBaseUrl(),
                route.getTimeoutMs(),
                route.getPriority(),
                route.isEnabled(),
                route.getCreatedAt(),
                route.getUpdatedAt());
    }
}
