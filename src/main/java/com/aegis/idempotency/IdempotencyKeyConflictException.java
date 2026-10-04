package com.aegis.idempotency;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

/** A key is already bound to a different request fingerprint. */
public class IdempotencyKeyConflictException extends AegisException {
    public IdempotencyKeyConflictException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key was already used for a different request");
    }
}
