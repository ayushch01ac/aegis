package com.aegis.proxy;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient.Builder} used by {@link DownstreamHttpClient}.
 *
 * <p>The shared builder sets the connect-side TCP timeout from {@link ProxyProperties}.
 * Read timeout is applied per call, derived from the individual route's {@code timeoutMs}.
 *
 * <p>{@link SimpleClientHttpRequestFactory} is used to keep the dependency footprint small.
 * A future phase may replace it with a pooled Apache HttpComponents factory when connection
 * reuse under load is a demonstrated need.
 */
@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyClientConfig {

    @Bean
    RestClient.Builder restClientBuilder(ProxyProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        // Read timeout is set per-request via the route's timeoutMs; no global default here.
        return RestClient.builder().requestFactory(factory);
    }
}
