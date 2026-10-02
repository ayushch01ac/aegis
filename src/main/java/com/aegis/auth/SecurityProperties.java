package com.aegis.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aegis.security")
public record SecurityProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration tokenTtl,
        @Valid @NotNull BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(@NotBlank String username, @NotBlank String password) {}
}
