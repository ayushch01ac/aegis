package com.aegis.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aegis.ratelimit.RateLimitExceededException;
import com.aegis.ratelimit.RateLimitResult;
import com.aegis.ratelimit.RateLimitService;
import com.aegis.circuitbreaker.CircuitBreaker;
import com.aegis.circuitbreaker.CircuitBreakerProperties;
import com.aegis.retry.RetryExecutor;
import com.aegis.retry.RetryProperties;
import com.aegis.route.Route;
import com.aegis.route.RouteNotFoundException;
import com.aegis.route.RoutePriority;
import com.aegis.route.RouteRepository;
import java.util.Optional;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private DownstreamHttpClient httpClient;

    @Mock
    private RateLimitService rateLimitService;

    private ProxyService proxyService;

    @BeforeEach
    void setUp() {
        RetryProperties retryProperties = new RetryProperties(false, 3, 0, 2, false);
        proxyService = new ProxyService(routeRepository, httpClient, rateLimitService,
                new RetryExecutor(retryProperties), retryProperties,
                new CircuitBreaker(new CircuitBreakerProperties(false, 5, 1_000, 1), Clock.systemUTC()),
                new com.aegis.scheduling.PriorityExecutionService(
                        new com.aegis.scheduling.ExecutionProperties(false, 1, 1)));
    }

    @Test
    void missingRouteThrowsRouteNotFoundException() {
        when(routeRepository.findByName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proxyService.proxy(
                "unknown", "", null, HttpMethod.GET, new HttpHeaders(), null))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void disabledRouteThrowsRouteDisabledException() {
        Route disabled = new Route("svc", "http://svc:8080", 2000, RoutePriority.NORMAL, false);
        when(routeRepository.findByName("svc")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> proxyService.proxy(
                "svc", "", null, HttpMethod.GET, new HttpHeaders(), null))
                .isInstanceOf(RouteDisabledException.class)
                .hasMessageContaining("svc");
    }

    @Test
    void rateLimitExceededThrowsRateLimitExceededException() {
        Route route = new Route("orders", "http://orders:8080", 2000, RoutePriority.HIGH, true);
        when(routeRepository.findByName("orders")).thenReturn(Optional.of(route));
        when(rateLimitService.evaluateRateLimit(route)).thenReturn(RateLimitResult.reject(10, 5));

        assertThatThrownBy(() -> proxyService.proxy(
                "orders", "", null, HttpMethod.GET, new HttpHeaders(), null))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("orders");
    }

    @Test
    void enabledRouteForwardsToClient() {
        Route route = new Route("orders", "http://orders:8080", 2000, RoutePriority.HIGH, true);
        when(routeRepository.findByName("orders")).thenReturn(Optional.of(route));
        when(rateLimitService.evaluateRateLimit(route)).thenReturn(RateLimitResult.allow(100, 99));

        HttpHeaders responseHeaders = new HttpHeaders();
        ResponseEntity<byte[]> downstreamResponse =
                ResponseEntity.ok().headers(responseHeaders).body(new byte[]{});
        HttpHeaders clientHeaders = new HttpHeaders();

        when(httpClient.forward(HttpMethod.GET, java.net.URI.create("http://orders:8080"),
                clientHeaders, null, "orders", 2000))
                .thenReturn(downstreamResponse);

        ResponseEntity<byte[]> result = proxyService.proxy(
                "orders", "", null, HttpMethod.GET, clientHeaders, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(result.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("99");
    }

    @Test
    void pathSuffixIsAppendedToBaseUrl() {
        Route route = new Route("orders", "http://orders:8080", 2000, RoutePriority.HIGH, true);
        when(routeRepository.findByName("orders")).thenReturn(Optional.of(route));
        when(rateLimitService.evaluateRateLimit(route)).thenReturn(RateLimitResult.allow(100, 99));

        ResponseEntity<byte[]> downstreamResponse = ResponseEntity.ok().body(new byte[]{});
        HttpHeaders clientHeaders = new HttpHeaders();

        when(httpClient.forward(HttpMethod.GET, java.net.URI.create("http://orders:8080/orders/42"),
                clientHeaders, null, "orders", 2000))
                .thenReturn(downstreamResponse);

        ResponseEntity<byte[]> result = proxyService.proxy(
                "orders", "/orders/42", null, HttpMethod.GET, clientHeaders, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void queryStringIsAppendedToUri() {
        Route route = new Route("search", "http://search:9200", 3000, RoutePriority.NORMAL, true);
        when(routeRepository.findByName("search")).thenReturn(Optional.of(route));
        when(rateLimitService.evaluateRateLimit(route)).thenReturn(RateLimitResult.allow(100, 99));

        ResponseEntity<byte[]> downstreamResponse = ResponseEntity.ok().body(new byte[]{});
        HttpHeaders clientHeaders = new HttpHeaders();

        when(httpClient.forward(HttpMethod.GET, java.net.URI.create("http://search:9200/_search?q=aegis"),
                clientHeaders, null, "search", 3000))
                .thenReturn(downstreamResponse);

        ResponseEntity<byte[]> result = proxyService.proxy(
                "search", "/_search", "q=aegis", HttpMethod.GET, clientHeaders, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trailingSlashOnBaseUrlIsNormalized() {
        Route route = new Route("items", "http://items:8080/", 1000, RoutePriority.LOW, true);
        when(routeRepository.findByName("items")).thenReturn(Optional.of(route));
        when(rateLimitService.evaluateRateLimit(route)).thenReturn(RateLimitResult.allow(100, 99));

        ResponseEntity<byte[]> downstreamResponse = ResponseEntity.ok().body(new byte[]{});
        HttpHeaders clientHeaders = new HttpHeaders();

        when(httpClient.forward(HttpMethod.GET, java.net.URI.create("http://items:8080/v1/items"),
                clientHeaders, null, "items", 1000))
                .thenReturn(downstreamResponse);

        ResponseEntity<byte[]> result = proxyService.proxy(
                "items", "/v1/items", null, HttpMethod.GET, clientHeaders, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
