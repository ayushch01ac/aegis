package com.aegis.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aegis.execution")
public record ExecutionProperties(
        boolean enabled,
        @Min(1) @Max(128) int maxWorkers,
        @Min(1) @Max(10_000) int queueCapacity) {
}
