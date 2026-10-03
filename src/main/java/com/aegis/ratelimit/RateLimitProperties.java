package com.aegis.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aegis.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private RateLimitAlgorithm defaultAlgorithm = RateLimitAlgorithm.FIXED_WINDOW;
    private int defaultCapacity = 100;
    private int defaultWindowSeconds = 60;
    private int defaultRefillRate = 10;
    private boolean failOpen = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimitAlgorithm getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(RateLimitAlgorithm defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public int getDefaultWindowSeconds() {
        return defaultWindowSeconds;
    }

    public void setDefaultWindowSeconds(int defaultWindowSeconds) {
        this.defaultWindowSeconds = defaultWindowSeconds;
    }

    public int getDefaultRefillRate() {
        return defaultRefillRate;
    }

    public void setDefaultRefillRate(int defaultRefillRate) {
        this.defaultRefillRate = defaultRefillRate;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
