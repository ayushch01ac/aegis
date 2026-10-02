package com.aegis.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI aegisOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aegis API")
                        .version("v1")
                        .description("Administrative APIs for the Aegis traffic-management platform."));
    }
}
