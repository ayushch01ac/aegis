package com.aegis.proxy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the downstream HTTP proxy.
 *
 * <p>These values are shared defaults. Individual routes may override {@code timeoutMs}
 * through their own stored configuration.
 */
@Validated
@ConfigurationProperties(prefix = "aegis.proxy")
public record ProxyProperties(
        @Min(1) @Max(30_000) int connectTimeoutMs,
        @Min(1) @Max(120_000) int defaultReadTimeoutMs) {

    public ProxyProperties {
        // connectTimeoutMs is the TCP-handshake budget; defaultReadTimeoutMs is
        // the per-request read budget when a route has no timeoutMs of its own.
    }
}
