package com.aegis.auth;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AegisException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }
}
