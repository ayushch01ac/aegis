package com.aegis.idempotency;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** The original request is still executing after the configured bounded wait. */
public class IdempotencyInProgressException extends AegisException {
    public IdempotencyInProgressException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                "A request with this Idempotency-Key is still being processed", retryHeaders());
    }

    private static HttpHeaders retryHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "1");
        return headers;
    }
}
