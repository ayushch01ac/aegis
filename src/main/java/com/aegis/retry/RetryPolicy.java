package com.aegis.retry;

import com.aegis.route.Route;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

/**
 * Stateless policy that decides, given an attempt number and the last outcome,
 * whether the retry loop should make another attempt or stop.
 *
 * <p>Idempotency rule: methods that are safe to repeat at the protocol level
 * (GET, HEAD, OPTIONS, PUT) are retried on both network errors and 5xx responses.
 * Non-idempotent methods (POST, PATCH, DELETE) are only retried when the route's
 * {@code retryOnNonIdempotent} flag is explicitly {@code true}, and even then only
 * on network-level failures — never on 5xx responses, because the downstream may
 * have already applied the mutation.
 *
 * <p>4xx responses are never retried; they indicate a client error that a retry
 * cannot fix.
 *
 * <p>{@link RetryPolicy} is intentionally stateless; {@link RetryExecutor} owns the
 * mutable attempt counter and the backoff state.
 */
public class RetryPolicy {

    private static final Set<HttpMethod> IDEMPOTENT_METHODS = Set.of(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
            HttpMethod.PUT);

    private final int maxAttempts;
    private final boolean retryOnNonIdempotent;
    private final long initialBackoffMs;
    private final double backoffMultiplier;

    public RetryPolicy(int maxAttempts, boolean retryOnNonIdempotent, long initialBackoffMs, double backoffMultiplier) {
        this.maxAttempts = maxAttempts;
        this.retryOnNonIdempotent = retryOnNonIdempotent;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    public RetryPolicy(int maxAttempts, boolean retryOnNonIdempotent) {
        this(maxAttempts, retryOnNonIdempotent, 100L, 2.0);
    }

    public static RetryPolicy fromRouteOrDefault(Route route, RetryProperties defaults) {
        int maxAttempts = route.getRetryMaxAttempts() != null ? route.getRetryMaxAttempts() : defaults.defaultMaxAttempts();
        boolean retryOnNonIdempotent = route.getRetryOnNonIdempotent() != null ? route.getRetryOnNonIdempotent() : defaults.defaultRetryOnNonIdempotent();
        long initialBackoffMs = route.getRetryInitialBackoffMs() != null ? route.getRetryInitialBackoffMs() : defaults.defaultInitialBackoffMs();
        double backoffMultiplier = route.getRetryBackoffMultiplier() != null ? route.getRetryBackoffMultiplier() : defaults.defaultBackoffMultiplier();
        return new RetryPolicy(maxAttempts, retryOnNonIdempotent, initialBackoffMs, backoffMultiplier);
    }

    /**
     * Returns whether the loop should make another attempt.
     *
     * @param attemptsMade    the number of attempts completed so far (starts at 1)
     * @param statusCode      the HTTP status of the last response, or {@code null} when
     *                        the call failed with a network exception before a response arrived
     * @param lastException   the exception from the last attempt, or {@code null} when a
     *                        response was received
     * @param method          the HTTP method being proxied
     */
    public RetryDecision shouldRetry(
            int attemptsMade,
            HttpStatusCode statusCode,
            Throwable lastException,
            HttpMethod method) {

        if (attemptsMade >= maxAttempts) {
            return RetryDecision.STOP;
        }

        boolean isIdempotent = IDEMPOTENT_METHODS.contains(method);

        if (lastException != null) {
            // Network failure (timeout, connection refused, etc.)
            // Idempotent: always retry. Non-idempotent: only when explicitly opted in.
            return (isIdempotent || retryOnNonIdempotent) ? RetryDecision.RETRY : RetryDecision.STOP;
        }

        if (statusCode != null && statusCode.is5xxServerError()) {
            // 5xx server error — only retry idempotent methods to avoid duplicate mutations.
            return isIdempotent ? RetryDecision.RETRY : RetryDecision.STOP;
        }

        // 1xx, 2xx, 3xx, 4xx — do not retry.
        return RetryDecision.STOP;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isRetryOnNonIdempotent() {
        return retryOnNonIdempotent;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }
}
