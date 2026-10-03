# Architecture

## Current implementation

Aegis is a modular monolith: one Spring Boot deployment owns the HTTP API, authentication, route configuration, controlled downstream proxying, Redis infrastructure, and distributed rate limiting. Phase 0 (foundation), Phase 1 (route management), Phase 2 (authentication and authorization), Phase 3 (controlled downstream proxy), Phase 4 (Redis connectivity and health checks), and Phase 5 (distributed rate limiting) are complete.

```text
Client
  |
  v
Aegis (Spring Boot)
  |-- Request ID filter
  |-- Status and actuator health endpoints (PostgreSQL + Redis)
  |-- JWT authentication and role authorization
  |-- Route administration API (including per-route rate limit configuration)
  |-- Rate Limit evaluation (Redis-backed atomic Fixed Window / Token Bucket)
  |-- Proxy: resolves route → evaluates rate limit → builds URI → forwards with per-route timeout
  |-- Validation and structured error handling
  |
  +---> PostgreSQL (Flyway migrations, DDL validate)
  +---> Redis (Lettuce, StringRedisTemplate, Actuator RedisHealthIndicator, Lua script limiters)
  +---> Downstream services (via RestClient)
```

Docker Compose starts PostgreSQL and Redis. Flyway owns the database schema, Hibernate validates it at startup, Spring Data Redis (Lettuce) manages Redis connectivity, and Lua scripts execute atomic rate-limiting decisions.

### Packages

```text
com.aegis
  AegisApplication
  common      configuration (including Redis), request IDs, errors, pagination, status endpoint, OpenAPI
  auth        JWT authentication and role authorization
  route       downstream-service configuration CRUD
  proxy       controlled downstream HTTP forwarding
  ratelimit   Redis-backed distributed rate limiting (fixed window and token bucket)
```

### Route module

A route is durable configuration for a downstream service: its name, base URL, timeout, default priority, enabled state, and rate-limiting policy (`rateLimitAlgorithm`, `rateLimitCapacity`, `rateLimitWindowSeconds`, `rateLimitRefillRate`). JPA entities remain inside the module; controllers accept and return DTOs.

`ux_routes_name` provides uniqueness and supports the create/rename lookup path. Route lists are paginated to prevent unbounded result sets.

### Rate limiting module

`com.aegis.ratelimit` implements Redis-backed distributed rate limiting:

1. **Fixed Window (`FixedWindowRateLimiter`):** Divides time into explicit windows of duration `windowSeconds`. Uses an atomic Redis `INCR` and `EXPIRE` Lua script to count requests within each window.
2. **Token Bucket (`TokenBucketRateLimiter`):** Maintains a token bucket refilled continuously at `refillRate` tokens per second up to `capacity`. Atomic refill and token consumption are executed via a Redis Lua script.
3. **RateLimitService:** Evaluates rate limits using either the route's explicit rate limit policy or global application defaults (`aegis.rate-limit.*`). Supports configurable `failOpen` mode if Redis is temporarily unreachable.
4. **Headers & Errors:** Exceeded limits return `429 TOO_MANY_REQUESTS` (`RATE_LIMIT_EXCEEDED`) with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` headers. Allowed requests propagate `X-RateLimit-Limit` and `X-RateLimit-Remaining` to the client response.

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

PostgreSQL holds durable configuration and metadata. Redis provides shared, low-latency, short-lived state such as rate-limit buckets and idempotency coordination. Kafka will be introduced after the synchronous request path is stable, so normal responses do not depend on consumers. Prometheus and Grafana are deferred until meaningful metrics exist.

## Phased evolution

1. **Complete:** foundation and route management.
2. **Complete:** JWT authentication with `ADMIN`, `OPERATOR`, and `VIEWER` permissions for administration APIs.
3. **Complete:** controlled proxy at `/api/v1/proxy/{routeName}/**` with per-route explicit connection/read timeouts, hop-by-hop header stripping, and structured downstream-error mapping.
4. **Complete:** Redis connectivity via Spring Data Redis (Lettuce), `StringRedisTemplate`, and Actuator health check integration.
5. **Complete:** distributed atomic fixed-window and token-bucket rate limiting using Redis Lua scripts.
6. **Next:** add configured, idempotency-aware retries and a testable `CLOSED` / `OPEN` / `HALF_OPEN` circuit breaker.
7. Add bounded workers, bounded priority queues, and predictable overload rejection.
8. Add idempotency semantics, Kafka events, observability, expanded tests, measured load testing, production-like Compose, and CI in that order.

Each phase must compile, pass relevant tests, update this document and the API contract, and state its trade-offs. No current document makes unmeasured performance claims.
