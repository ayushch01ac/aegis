package com.aegis.scheduling;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class OverloadedException extends AegisException {
    public OverloadedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PROXY_OVERLOADED",
                "Proxy execution capacity is temporarily exhausted", retryAfterHeader());
    }

    private static HttpHeaders retryAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "1");
        return headers;
    }
}
