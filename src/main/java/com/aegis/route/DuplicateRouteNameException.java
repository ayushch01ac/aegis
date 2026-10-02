package com.aegis.route;

import com.aegis.common.error.AegisException;
import org.springframework.http.HttpStatus;

public class DuplicateRouteNameException extends AegisException {

    public DuplicateRouteNameException(String name) {
        super(HttpStatus.CONFLICT, "ROUTE_NAME_CONFLICT", "Route name already exists: " + name);
    }
}
