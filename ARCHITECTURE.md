# Architecture

## Current implementation

Aegis is a modular monolith: one Spring Boot deployment owns the HTTP API and route configuration. Phase 0 (foundation) and Phase 1 (route management) are complete.

```text
Client
  |
  v
Aegis (Spring Boot)
  |-- Request ID filter
  |-- Status and actuator endpoints
  |-- Route administration API
  |-- Validation and structured error handling
  |
  v
PostgreSQL
```

Docker Compose currently starts PostgreSQL only. Flyway owns the database schema, and Hibernate validates it at startup.

### Packages

```text
com.aegis
  AegisApplication
  common   configuration, request IDs, errors, pagination, status endpoint, OpenAPI
  route    downstream-service configuration CRUD
```

### Route module

A route is durable configuration for a downstream service: its name, base URL, timeout, default priority, and enabled state. JPA entities remain inside the module; controllers accept and return DTOs.

The route administration API is currently the only request path. Saving a route does not proxy traffic, execute a health check, or apply a retry/rate-limit/circuit-breaker policy.

`ux_routes_name` provides uniqueness and supports the create/rename lookup path. Route lists are paginated to prevent unbounded result sets.

### Current security posture

Spring Security is present so its integration can evolve in place, but every request is intentionally permitted during Phase 1. CSRF, form login, and HTTP Basic are disabled; the application is stateless. This is not an authenticated or authorization-protected deployment yet.

## Target architecture

The target remains one deployable application with logical modules, not prematurely split microservices.

```text
Client
  |
  v
Authentication -> validation -> rate limit -> idempotency -> priority/backpressure
  -> circuit breaker -> proxy execution -> retry when explicitly allowed
  |
  v
Response + metrics/logs/events
```

Planned module boundaries:

```text
com.aegis
  auth             JWT authentication and role authorization
  route            downstream configuration
  proxy            controlled downstream HTTP forwarding
  ratelimit        Redis-backed distributed limits
  retry            transient-failure retry policy
  circuitbreaker   downstream protection state machine
  idempotency      duplicate-operation protection
  scheduling       bounded priority execution and backpressure
  messaging        Kafka event publication and consumers
  observability    metrics, tracing-compatible correlation, operational logging
  audit            durable audit concerns
  common           shared HTTP and configuration concerns
```

PostgreSQL will hold durable configuration and metadata. Redis will be introduced only for shared, low-latency, short-lived state such as rate-limit buckets and idempotency coordination. Kafka will be introduced after the synchronous request path is stable, so normal responses do not depend on consumers. Prometheus and Grafana are deferred until meaningful metrics exist.

## Phased evolution

1. **Complete:** foundation and route management.
2. **Next:** JWT authentication with `ADMIN`, `OPERATOR`, and `VIEWER` permissions for administration APIs.
3. Add the simplest controlled proxy with explicit connection/response timeouts and safe downstream-error mapping.
4. Add Redis infrastructure, then an atomic fixed-window limiter followed by token bucket.
5. Add idempotency-aware retries and a testable `CLOSED` / `OPEN` / `HALF_OPEN` circuit breaker.
6. Add bounded workers, bounded priority queues, and predictable overload rejection.
7. Add idempotency semantics, Kafka events, observability, expanded tests, measured load testing, production-like Compose, and CI in that order.

Each phase must compile, pass relevant tests, update this document and the API contract, and state its trade-offs. No current document makes unmeasured performance claims.
