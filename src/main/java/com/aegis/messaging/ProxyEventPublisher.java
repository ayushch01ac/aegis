package com.aegis.messaging;

/** Publishes proxy events without affecting the request's HTTP result. */
public interface ProxyEventPublisher {
    void publish(ProxyCompletedEvent event);
}
