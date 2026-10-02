package com.aegis.common.error;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String requestId,
        Map<String, String> fieldErrors) {}
