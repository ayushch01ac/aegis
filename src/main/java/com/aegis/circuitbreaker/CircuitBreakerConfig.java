package com.aegis.circuitbreaker;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CircuitBreakerConfig {

    @Bean
    Clock circuitBreakerClock() {
        return Clock.systemUTC();
    }
}
