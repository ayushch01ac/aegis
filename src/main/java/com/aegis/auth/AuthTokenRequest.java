package com.aegis.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthTokenRequest(
        @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}
