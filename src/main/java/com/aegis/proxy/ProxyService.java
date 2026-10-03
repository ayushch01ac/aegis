package com.aegis.proxy;

import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.aegis.ratelimit.RateLimitExceededException;
import com.aegis.ratelimit.RateLimitResult;
import com.aegis.ratelimit.RateLimitService;
import com.aegis.route.Route;
import com.aegis.route.RouteNotFoundException;
import com.aegis.route.RouteRepository;

/**
 * Orchestrates a single proxied request.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Look up the named route; throw {@link RouteNotFoundException} when absent.</li>
 *   <li>Reject disabled routes with {@link RouteDisabledException}.</li>
 *   <li>Evaluate Redis distributed rate limiting via {@link RateLimitService}.</li>
 *   <li>Compose the downstream URI by appending {@code pathSuffix} to the route base URL.</li>
 *   <li>Delegate the HTTP call to {@link DownstreamHttpClient} with the route's own timeout.</li>
 * </ol>
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final RouteRepository routeRepository;
    private final DownstreamHttpClient httpClient;
    private final RateLimitService rateLimitService;

    public ProxyService(
            RouteRepository routeRepository,
            DownstreamHttpClient httpClient,
            RateLimitService rateLimitService) {
        this.routeRepository = routeRepository;
        this.httpClient = httpClient;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Forwards a client request to the downstream service identified by {@code routeName}.
     *
     * @param routeName   the route's unique name
     * @param pathSuffix  additional path appended to the route's base URL (may be empty)
     * @param queryString raw query string from the incoming request (may be null)
     * @param method      HTTP method to forward
     * @param headers     filtered incoming headers
     * @param body        raw request body (may be null for bodyless methods)
     * @return the downstream response, status and headers preserved
     */
    public ResponseEntity<byte[]> proxy(
            String routeName,
            String pathSuffix,
            String queryString,
            HttpMethod method,
            HttpHeaders headers,
            byte[] body) {

        Route route = routeRepository
                .findByName(routeName)
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeName));

        if (!route.isEnabled()) {
            throw new RouteDisabledException(routeName);
        }

        RateLimitResult rateLimitResult = rateLimitService.evaluateRateLimit(route);
        if (!rateLimitResult.allowed()) {
            throw new RateLimitExceededException(routeName, rateLimitResult);
        }

        URI downstream = buildUri(route.getBaseUrl(), pathSuffix, queryString, routeName);
        log.info("Proxy route={} method={} uri={}", routeName, method, downstream);

        ResponseEntity<byte[]> response = httpClient.forward(
                method, downstream, headers, body, routeName, route.getTimeoutMs());

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(response.getHeaders());
        responseHeaders.set("X-RateLimit-Limit", String.valueOf(rateLimitResult.limit()));
        responseHeaders.set("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));

        return ResponseEntity.status(response.getStatusCode())
                .headers(responseHeaders)
                .body(response.getBody());
    }

    private static URI buildUri(String baseUrl, String pathSuffix, String queryString, String routeName) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = (pathSuffix == null || pathSuffix.isBlank()) ? "" : pathSuffix;
        if (!suffix.isEmpty() && !suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        String raw = base + suffix + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
        try {
            return new URI(raw);
        } catch (URISyntaxException ex) {
            throw new DownstreamException(routeName, "Could not build downstream URI: " + ex.getMessage());
        }
    }
}
