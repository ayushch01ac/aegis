package com.aegis.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aegis.api")
public record ApiProperties(int defaultPageSize, int maxPageSize) {}
