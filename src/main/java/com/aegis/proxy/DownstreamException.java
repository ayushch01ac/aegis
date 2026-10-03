package com.aegis.proxy;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the downstream call fails due to a network error or an explicit timeout.
 *
 * <p>The upstream error is included as the cause for logging but is not propagated to the client.
 */
public class DownstreamException extends AegisException {

    public DownstreamException(String routeName, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_ERROR",
                "Downstream call failed for route: " + routeName);
        initCause(cause);
    }

    public DownstreamException(String routeName, String detail) {
        super(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_ERROR",
                "Downstream call failed for route: " + routeName + " — " + detail);
    }
}
