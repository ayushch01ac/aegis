package com.aegis.retry;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

/**
 * Thrown by {@link RetryExecutor} after all configured retry attempts have been consumed
 * and the downstream call still failed.
 *
 * <p>Maps to {@code 502 Bad Gateway} with error code {@code RETRY_EXHAUSTED}. The
 * {@code X-Retry-Attempts} response header is populated by {@link RetryExecutor} before
 * this exception is constructed so that callers can observe how many attempts were made.
 */
public class RetryExhaustedException extends AegisException {

    private final int attemptsConsumed;

    public RetryExhaustedException(String routeName, int attemptsConsumed, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "RETRY_EXHAUSTED",
                "Downstream call failed after " + attemptsConsumed
                        + " attempt(s) for route: " + routeName);
        this.attemptsConsumed = attemptsConsumed;
        getHeaders().set("X-Retry-Attempts", String.valueOf(attemptsConsumed));
        if (cause != null) {
            initCause(cause);
        }
    }

    public int getAttemptsConsumed() {
        return attemptsConsumed;
    }
}
