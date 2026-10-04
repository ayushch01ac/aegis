package com.aegis.retry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link RetryProperties} binding and makes the retry module self-contained.
 *
 * <p>{@link RetryExecutor} is a Spring component and is picked up by component scanning;
 * this class exists solely to register the {@code @ConfigurationProperties} bean without
 * requiring a caller to add {@code @EnableConfigurationProperties} elsewhere.
 */
@Configuration
@EnableConfigurationProperties(RetryProperties.class)
public class RetryConfig {
}
