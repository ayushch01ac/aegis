package com.aegis.route;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Route() {}

    public Route(String name, String baseUrl, int timeoutMs, RoutePriority priority, boolean enabled) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.priority = priority;
        this.enabled = enabled;
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

    public void applyUpdate(String name, String baseUrl, int timeoutMs, RoutePriority priority, boolean enabled) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.priority = priority;
        this.enabled = enabled;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
