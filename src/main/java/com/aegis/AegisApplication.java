package com.aegis;

import com.aegis.auth.SecurityProperties;
import com.aegis.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({SecurityProperties.class, RateLimitProperties.class})
public class AegisApplication {

    public static void main(String[] args) {
        SpringApplication.run(AegisApplication.class, args);
    }
}
