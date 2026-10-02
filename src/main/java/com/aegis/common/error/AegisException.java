package com.aegis.common.error;

import org.springframework.http.HttpStatus;

public class AegisException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AegisException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
