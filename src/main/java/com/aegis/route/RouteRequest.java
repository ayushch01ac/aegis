package com.aegis.route;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RouteRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String name,
        @NotBlank @Size(max = 500) @HttpUrl String baseUrl,
        @NotNull @Min(1) @Max(120_000) Integer timeoutMs,
        @NotNull RoutePriority priority,
        Boolean enabled) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
