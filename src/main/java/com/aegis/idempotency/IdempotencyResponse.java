package com.aegis.idempotency;

import org.springframework.http.ResponseEntity;

/** Result of an idempotent execution, including whether it was served from Redis. */
public record IdempotencyResponse(ResponseEntity<byte[]> response, boolean replayed) {
}
