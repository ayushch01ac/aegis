package com.aegis.proxy;

import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

/**
 * Thin wrapper around {@link RestClient} that enforces an explicit per-request read timeout.
 *
 * <p>A new {@link RestClient} instance is constructed for each call so that the per-route
 * {@code timeoutMs} can be applied as a request-level read timeout without sharing mutable state.
 * Each call gets its own lightweight request factory so both timeouts are explicit.
 *
 * <p>Design trade-off: building a per-call client is slightly more expensive than a shared
 * client, but it keeps timeout enforcement simple and correct without thread-local state or a
 * custom interceptor. When the routing layer gains per-route policies this may be revisited.
 */
@Component
public class DownstreamHttpClient {

    private static final Logger log = LoggerFactory.getLogger(DownstreamHttpClient.class);

    private final Builder baseBuilder;
    private final ProxyProperties properties;

    public DownstreamHttpClient(Builder restClientBuilder, ProxyProperties properties) {
        this.baseBuilder = restClientBuilder;
        this.properties = properties;
    }

    /**
     * Forwards {@code method} to {@code uri} with the given body and headers, using
     * {@code readTimeoutMs} as the read-side deadline.
     *
     * @return the full downstream {@link ResponseEntity}, preserving status, headers, and body
     * @throws DownstreamException if the call times out, the connection is refused, or the
     *     response body cannot be read
     */
    public ResponseEntity<byte[]> forward(
            HttpMethod method,
            URI uri,
            HttpHeaders headers,
            byte[] body,
            String routeName,
            int readTimeoutMs) {

        log.debug("Proxying {} {} route={} timeout={}ms", method, uri, routeName, readTimeoutMs);

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
            return baseBuilder.clone()
                    .requestFactory(factory)
                    .build()
                    .method(method)
                    .uri(uri)
                    .headers(h -> h.addAll(filterHopByHopHeaders(headers)))
                    .body(body != null ? body : new byte[0])
                    .retrieve()
                    .onStatus(status -> true, (req, resp) -> {
                        // Do not throw — let the caller propagate the status as-is.
                    })
                    .toEntity(byte[].class);
        } catch (ResourceAccessException ex) {
            // Covers connect timeout, read timeout, and connection-refused.
            log.warn("Downstream call failed: route={} uri={} reason={}", routeName, uri, ex.getMessage());
            throw new DownstreamException(routeName, ex);
        } catch (Exception ex) {
            log.warn("Unexpected downstream error: route={} uri={}", routeName, uri, ex);
            throw new DownstreamException(routeName, ex);
        }
    }

    /**
     * Removes hop-by-hop headers that must not be forwarded to the downstream service.
     * This is a minimal safe-default list; SSRF controls are enforced at route-creation time.
     */
    private static HttpHeaders filterHopByHopHeaders(HttpHeaders incoming) {
        HttpHeaders filtered = new HttpHeaders();
        filtered.addAll(incoming);
        filtered.remove(HttpHeaders.HOST);
        filtered.remove(HttpHeaders.CONNECTION);
        filtered.remove("Keep-Alive");
        filtered.remove("Proxy-Authorization");
        filtered.remove("TE");
        filtered.remove("Trailers");
        filtered.remove("Transfer-Encoding");
        filtered.remove("Upgrade");
        return filtered;
    }
}
