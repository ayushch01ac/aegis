package com.aegis.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
@EnableConfigurationProperties(ApiProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(ApiProperties apiProperties) {
        return resolver -> {
            resolver.setMaxPageSize(apiProperties.maxPageSize());
            resolver.setFallbackPageable(PageRequest.of(0, apiProperties.defaultPageSize()));
        };
    }
}
