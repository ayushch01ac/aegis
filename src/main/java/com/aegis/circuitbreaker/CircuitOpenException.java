package com.aegis.circuitbreaker;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class CircuitOpenException extends AegisException {

    public CircuitOpenException(String routeName, long retryAfterSeconds) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN",
                "Downstream circuit is open for route: " + routeName, retryAfterHeader(retryAfterSeconds));
    }

    private static HttpHeaders retryAfterHeader(long retryAfterSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        return headers;
    }
}
