package com.aegis.proxy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.aegis.common.error.RequestId;
import com.aegis.common.web.RequestIdFilter;

/**
 * Entry point for proxied traffic.
 *
 * <p>Any authenticated request matching {@code /api/v1/proxy/{routeName}/**} is forwarded
 * to the downstream service registered under {@code routeName}. The downstream response
 * status, headers, and body are returned verbatim, except for hop-by-hop headers which
 * are stripped by {@link DownstreamHttpClient}.
 *
 * <p>The {@code X-Request-Id} header is propagated to the downstream service so that
 * distributed traces can be correlated.
 */
@RestController
@Tag(name = "Proxy", description = "Controlled downstream HTTP proxy")
public class ProxyController {

    static final String PROXY_PATH = "/api/v1/proxy/{routeName}/**";

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping(PROXY_PATH)
    @Operation(
            summary = "Proxy a request to a configured downstream route",
            description = """
                    Forwards the incoming request to the downstream service registered under the
                    given route name. The route must be enabled. The downstream response status,
                    headers, and body are returned verbatim.

                    The path after /proxy/{routeName} is appended to the route base URL.
                    Example: GET /api/v1/proxy/orders-service/orders/42
                    → GET http://orders:8080/orders/42
                    """)
    public ResponseEntity<byte[]> proxy(
            @Parameter(description = "Name of the configured route") @PathVariable String routeName,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request) {

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = extractHeaders(request);

        // Forward the correlation ID so downstream services can log it.
        String requestId = MDC.get(RequestId.MDC_KEY);
        if (requestId != null) {
            headers.set(RequestIdFilter.HEADER, requestId);
        }

        String pathSuffix = extractPathSuffix(request, routeName);
        String queryString = request.getQueryString();

        ResponseEntity<byte[]> downstream = proxyService.proxy(
                routeName, pathSuffix, queryString, method, headers, body);

        // Propagate downstream status and headers; the body is returned as-is.
        return ResponseEntity.status(downstream.getStatusCode())
                .headers(downstream.getHeaders())
                .body(downstream.getBody());
    }

    /**
     * Extracts the path after {@code /api/v1/proxy/{routeName}} from the request URI.
     * Spring's {@code /**} mapping captures the suffix in the attribute
     * {@code HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}.
     */
    private static String extractPathSuffix(HttpServletRequest request, String routeName) {
        String full = request.getRequestURI();
        // Find the position just after /api/v1/proxy/{routeName}
        String prefix = "/api/v1/proxy/" + routeName;
        if (full.startsWith(prefix)) {
            return full.substring(prefix.length());
        }
        return "";
    }

    private static HttpHeaders extractHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name ->
                Collections.list(request.getHeaders(name)).forEach(value ->
                        headers.add(name, value)));
        return headers;
    }
}
