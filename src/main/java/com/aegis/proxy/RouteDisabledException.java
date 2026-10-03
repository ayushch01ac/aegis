package com.aegis.proxy;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the named route exists but is currently disabled.
 */
public class RouteDisabledException extends AegisException {

    public RouteDisabledException(String routeName) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ROUTE_DISABLED",
                "Route is disabled: " + routeName);
    }
}
