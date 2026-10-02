package com.aegis.route;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

public class RouteNotFoundException extends AegisException {

    public RouteNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", message);
    }
}
