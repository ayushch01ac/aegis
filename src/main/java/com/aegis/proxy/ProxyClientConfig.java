package com.aegis.proxy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient.Builder} used by {@link DownstreamHttpClient}.
 *
 * <p>{@link DownstreamHttpClient} creates the lightweight request factory per call so both the
 * connect-side timeout and the route-specific read timeout can be explicit.
 *
 * <p>The builder remains shared only for common RestClient configuration. A future phase may
 * introduce a pooled client when measured load demonstrates a need.
 */
@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
