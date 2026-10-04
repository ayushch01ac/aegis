package com.aegis.retry;

/**
 * Outcome returned by {@link RetryPolicy#shouldRetry(int, org.springframework.http.HttpStatusCode,
 * Throwable, org.springframework.http.HttpMethod)} to guide the retry loop.
 *
 * <p>{@link #RETRY} means another attempt should be made after a backoff delay.
 * {@link #STOP} means the last response or exception should be propagated immediately.
 */
public enum RetryDecision {
    RETRY,
    STOP
}
