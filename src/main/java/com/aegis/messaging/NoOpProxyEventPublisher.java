package com.aegis.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps Kafka an explicit opt-in deployment dependency. */
@Component
@ConditionalOnProperty(prefix = "aegis.messaging.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
class NoOpProxyEventPublisher implements ProxyEventPublisher {
    @Override
    public void publish(ProxyCompletedEvent event) {
        // Kafka is disabled for this deployment.
    }
}
