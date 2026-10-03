# Architecture

## Current implementation

Aegis is a modular monolith: one Spring Boot deployment owns the HTTP API, authentication, route configuration, and controlled downstream proxying. Phase 0 (foundation), Phase 1 (route management), Phase 2 (authentication and authorization), and Phase 3 (controlled downstream proxy) are complete.

```text
Client
  |
  v
Aegis (Spring Boot)
  |-- Request ID filter
  |-- Status and actuator endpoints
  |-- JWT authentication and role authorization
  |-- Route administration API
  |-- Proxy: resolves route → builds URI → forwards with per-route timeout
  |-- Validation and structured error handling
  |
  v
PostgreSQL             Downstream services (via RestClient)
```

Docker Compose currently starts PostgreSQL only. Flyway owns the database schema, and Hibernate validates it at startup.

### Packages

```text
com.aegis
  AegisApplication
  common   configuration, request IDs, errors, pagination, status endpoint, OpenAPI
  route    downstream-service configuration CRUD
  proxy    controlled downstream HTTP forwarding
```

### Route module

A route is durable configuration for a downstream service: its name, base URL, timeout, default priority, and enabled state. JPA entities remain inside the module; controllers accept and return DTOs.

The route administration API is currently the only request path. Saving a route does not proxy traffic, execute a health check, or apply a retry/rate-limit/circuit-breaker policy.

`ux_routes_name` provides uniqueness and supports the create/rename lookup path. Route lists are paginated to prevent unbounded result sets.

### Current security posture

Spring Security validates HMAC-SHA256 Bearer JWTs. Signing material, token lifetime, and bootstrap administrator credentials are supplied through environment variables. A Flyway migration stores users with BCrypt password hashes and a single role. The bootstrap administrator is created only when its username does not already exist; credentials are never logged.

Route reads require `ADMIN`, `OPERATOR`, or `VIEWER`; route mutations require `ADMIN`. CSRF, form login, and HTTP Basic are disabled because the API is stateless. Security failures use the standard structured-error shape and include the request ID.

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
2. **Complete:** JWT authentication with `ADMIN`, `OPERATOR`, and `VIEWER` permissions for administration APIs.
3. **Complete:** controlled proxy at `/api/v1/proxy/{routeName}/**` with per-route explicit connection/read timeouts, hop-by-hop header stripping, and structured downstream-error mapping.
4. **Next:** add Redis infrastructure, then an atomic fixed-window limiter followed by token bucket.
5. Add idempotency-aware retries and a testable `CLOSED` / `OPEN` / `HALF_OPEN` circuit breaker.
6. Add bounded workers, bounded priority queues, and predictable overload rejection.
7. Add idempotency semantics, Kafka events, observability, expanded tests, measured load testing, production-like Compose, and CI in that order.

Each phase must compile, pass relevant tests, update this document and the API contract, and state its trade-offs. No current document makes unmeasured performance claims.
