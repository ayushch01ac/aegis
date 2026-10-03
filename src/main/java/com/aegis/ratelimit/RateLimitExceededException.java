package com.aegis.ratelimit;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends AegisException {

    private final RateLimitResult result;

    public RateLimitExceededException(String routeName, RateLimitResult result) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Rate limit exceeded for route: " + routeName + ". Try again in " + result.retryAfterSeconds() + " seconds.",
                buildHeaders(result));
        this.result = result;
    }

    public RateLimitResult getResult() {
        return result;
    }

    private static HttpHeaders buildHeaders(RateLimitResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(result.limit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        headers.set("Retry-After", String.valueOf(result.retryAfterSeconds()));
        return headers;
    }
}
