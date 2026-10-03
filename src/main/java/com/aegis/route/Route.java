package com.aegis.route;

import com.aegis.ratelimit.RateLimitAlgorithm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoutePriority priority;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_algorithm", length = 30)
    private RateLimitAlgorithm rateLimitAlgorithm;

    @Column(name = "rate_limit_capacity")
    private Integer rateLimitCapacity;

    @Column(name = "rate_limit_window_seconds")
    private Integer rateLimitWindowSeconds;

    @Column(name = "rate_limit_refill_rate")
    private Integer rateLimitRefillRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Route() {}

    public Route(
            String name,
            String baseUrl,
            int timeoutMs,
            RoutePriority priority,
            boolean enabled,
            RateLimitAlgorithm rateLimitAlgorithm,
            Integer rateLimitCapacity,
            Integer rateLimitWindowSeconds,
            Integer rateLimitRefillRate) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.priority = priority;
        this.enabled = enabled;
        this.rateLimitAlgorithm = rateLimitAlgorithm;
        this.rateLimitCapacity = rateLimitCapacity;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.rateLimitRefillRate = rateLimitRefillRate;
    }

    public Route(String name, String baseUrl, int timeoutMs, RoutePriority priority, boolean enabled) {
        this(name, baseUrl, timeoutMs, priority, enabled, null, null, null, null);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void applyUpdate(
            String name,
            String baseUrl,
            int timeoutMs,
            RoutePriority priority,
            boolean enabled,
            RateLimitAlgorithm rateLimitAlgorithm,
            Integer rateLimitCapacity,
            Integer rateLimitWindowSeconds,
            Integer rateLimitRefillRate) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.priority = priority;
        this.enabled = enabled;
        this.rateLimitAlgorithm = rateLimitAlgorithm;
        this.rateLimitCapacity = rateLimitCapacity;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.rateLimitRefillRate = rateLimitRefillRate;
    }

    public void applyUpdate(String name, String baseUrl, int timeoutMs, RoutePriority priority, boolean enabled) {
        applyUpdate(name, baseUrl, timeoutMs, priority, enabled, null, null, null, null);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public RoutePriority getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RateLimitAlgorithm getRateLimitAlgorithm() {
        return rateLimitAlgorithm;
    }

    public Integer getRateLimitCapacity() {
        return rateLimitCapacity;
    }

    public Integer getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public Integer getRateLimitRefillRate() {
        return rateLimitRefillRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
