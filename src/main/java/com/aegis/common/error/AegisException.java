package com.aegis.common.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class AegisException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final HttpHeaders headers;

    public AegisException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public AegisException(HttpStatus status, String code, String message, HttpHeaders headers) {
        super(message);
        this.status = status;
        this.code = code;
        this.headers = headers != null ? headers : HttpHeaders.EMPTY;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
}
