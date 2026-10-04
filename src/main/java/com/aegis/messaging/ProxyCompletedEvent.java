package com.aegis.messaging;

import java.time.Instant;
import java.util.UUID;

/** Non-sensitive audit/analytics event for a completed proxy response. */
public record ProxyCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        String requestId,
        String routeName,
        String method,
        int status,
        boolean idempotencyReplay) {
}
