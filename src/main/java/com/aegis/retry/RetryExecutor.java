package com.aegis.retry;

import com.aegis.proxy.DownstreamException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Executes a downstream call with bounded retries according to a {@link RetryPolicy}.
 *
 * <p>The loop:
 * <ol>
 *   <li>Invokes the supplier (a lambda over {@link com.aegis.proxy.DownstreamHttpClient#forward}).
 *   <li>If the attempt succeeds and the status is not retryable, return immediately.
 *   <li>If the attempt fails or the status is retryable, ask the policy whether to retry.
 *   <li>If the policy says {@link RetryDecision#RETRY}, sleep for the computed backoff and
 *       repeat from step 1.
 *   <li>When all attempts are exhausted, throw {@link RetryExhaustedException}.
 * </ol>
 *
 * <p>Backoff: {@code delay = initialBackoffMs * multiplier^(attempt - 1)}, capped at
 * {@code maxBackoffMs} (currently hard-coded to 30 000 ms). The sleep is interruptible;
 * an {@link InterruptedException} re-sets the interrupt flag and breaks the loop.
 *
 * <p>The response supplier must throw {@link DownstreamException} on network failure;
 * any other {@link RuntimeException} is propagated without retry.
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    /** Hard cap so a misconfigured multiplier cannot block a thread indefinitely. */
    private static final long MAX_BACKOFF_MS = 30_000L;

    private final RetryProperties properties;

    public RetryExecutor(RetryProperties properties) {
        this.properties = properties;
    }

    /**
     * Executes {@code attempt} with retries guided by {@code policy}.
     *
     * @param routeName the route name (for log/error context)
     * @param method    the HTTP method (governs idempotency checks)
     * @param policy    the resolved retry policy for this call
     * @param attempt   supplies the downstream response; must throw {@link DownstreamException}
     *                  on network/timeout failure
     * @return the final successful {@link ResponseEntity}
     * @throws RetryExhaustedException  if all attempts failed
     * @throws RuntimeException         for any non-retryable exception from the supplier
     */
    public ResponseEntity<byte[]> execute(
            String routeName,
            HttpMethod method,
            RetryPolicy policy,
            Supplier<ResponseEntity<byte[]>> attempt) {

        if (!properties.enabled()) {
            // Retry is globally disabled — execute exactly once, propagate directly.
            return attempt.get();
        }

        int attemptsMade = 0;
        Throwable lastException = null;
        ResponseEntity<byte[]> lastResponse = null;

        while (true) {
            lastException = null;
            try {
                lastResponse = attempt.get();
                attemptsMade++;
            } catch (DownstreamException ex) {
                // Network-level failure: record and let policy decide.
                lastException = ex;
                lastResponse = null;
                attemptsMade++;
                log.warn("Attempt {}/{} failed with network error: route={} reason={}",
                        attemptsMade, policy.getMaxAttempts(), routeName, ex.getMessage());
            }
            // Any other RuntimeException propagates immediately — it is not a transient failure.

            var statusCode = lastResponse != null ? lastResponse.getStatusCode() : null;

            RetryDecision decision = policy.shouldRetry(attemptsMade, statusCode, lastException, method);

            if (decision == RetryDecision.STOP) {
                if (lastException != null) {
                    throw new RetryExhaustedException(routeName, attemptsMade, lastException);
                }
                return lastResponse;
            }

            // decision == RETRY
            long backoffMs = computeBackoff(
                    policy.getInitialBackoffMs(), policy.getBackoffMultiplier(), attemptsMade);

            log.info("Retrying downstream call: route={} attempt={}/{} backoffMs={}",
                    routeName, attemptsMade, policy.getMaxAttempts(), backoffMs);

            if (backoffMs > 0) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException(routeName, attemptsMade,
                            lastException != null ? lastException : ie);
                }
            }
        }
    }

    /**
     * Computes the next backoff duration using an exponential strategy, capped at
     * {@link #MAX_BACKOFF_MS}.
     *
     * @param initialMs  the configured initial backoff in milliseconds
     * @param multiplier the configured growth factor (≥ 1)
     * @param attempt    the number of attempts completed so far (1-based)
     */
    static long computeBackoff(long initialMs, double multiplier, int attempt) {
        if (initialMs <= 0 || attempt < 1) {
            return 0L;
        }
        double raw = initialMs * Math.pow(multiplier, attempt - 1);
        return Math.min((long) raw, MAX_BACKOFF_MS);
    }
}
