package com.aegis.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aegis.messaging.kafka")
public record MessagingProperties(boolean enabled, @NotBlank String proxyEventsTopic) {
}
