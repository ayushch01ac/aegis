package com.aegis.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Best-effort asynchronous publisher; Kafka delivery never changes an HTTP response. */
@Component
@ConditionalOnProperty(prefix = "aegis.messaging.kafka", name = "enabled", havingValue = "true")
class KafkaProxyEventPublisher implements ProxyEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaProxyEventPublisher.class);

    private final KafkaTemplate<String, ProxyCompletedEvent> kafkaTemplate;
    private final MessagingProperties properties;

    KafkaProxyEventPublisher(KafkaTemplate<String, ProxyCompletedEvent> kafkaTemplate, MessagingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(ProxyCompletedEvent event) {
        try {
            kafkaTemplate.send(properties.proxyEventsTopic(), event.routeName(), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Kafka proxy event was not delivered: eventId={} route={} reason={}",
                                    event.eventId(), event.routeName(), error.getMessage());
                        }
                    });
        } catch (RuntimeException ex) {
            // Serialization or local producer failures must not affect proxy availability.
            log.warn("Kafka proxy event could not be submitted: eventId={} route={} reason={}",
                    event.eventId(), event.routeName(), ex.getMessage());
        }
    }
}
