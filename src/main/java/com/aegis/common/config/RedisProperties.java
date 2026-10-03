package com.aegis.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Aegis-specific Redis configuration properties.
 */
@Validated
@ConfigurationProperties(prefix = "aegis.redis")
public record RedisProperties(String keyPrefix) {

    public RedisProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "aegis:";
        }
    }
}
