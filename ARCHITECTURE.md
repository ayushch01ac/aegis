# Architecture

## Overview

Aegis is one Spring Boot deployment with logical modules. It is deliberately a modular monolith: the synchronous request pipeline is cohesive, while package boundaries keep each reliability concern understandable and independently testable.

The optional `demo/orders-service` is a separate Spring Boot application used only to demonstrate this boundary locally. It is not a module of the Aegis artifact and Aegis can proxy any compatible HTTP service, not only Spring Boot applications.

```text
Client
  |
  v
Aegis
  |-- request ID and structured errors
  |-- JWT authentication and role authorization
  |-- route administration API
  |-- Redis rate-limit decision
  |-- idempotency coordination when eligible
  |-- bounded priority admission
  |-- circuit breaker and retry policy
  |-- HTTP proxy with explicit timeouts
  |-- optional completed-response Kafka event
  |
  +---> PostgreSQL: routes and users
  +---> Redis: rate limits and idempotency records
  +---> Downstream HTTP services
  +---> Kafka: optional best-effort event sink
```

## Modules

```text
com.aegis
  common          request IDs, errors, pagination, status, OpenAPI, shared configuration
  auth            local users, BCrypt verification, JWTs, authorization
  route           persisted downstream-route configuration
  proxy           proxy controller, orchestration, downstream HTTP client
  ratelimit       Redis Lua fixed-window and token-bucket limiters
  retry           idempotency-aware retry policy and executor
  circuitbreaker  per-route CLOSED / OPEN / HALF_OPEN protection
  scheduling      fixed workers, bounded priority queue, overload rejection
  idempotency     Redis lease coordination and response replay
  messaging       opt-in Kafka completion-event publishing
```

## Request pipeline

For a protected proxy request, Spring Security authenticates the Bearer JWT first. The controller captures the raw request pieces and carries the correlation ID downstream. `ProxyService` then resolves the route, rejects disabled routes, evaluates the Redis rate limit, builds the target URI, and runs the downstream call through bounded priority admission, the route circuit breaker, and the retry executor.

`POST` and `PATCH` requests carrying `Idempotency-Key` take one additional outer step: Redis gives one matching request a temporary processing lease, coordinates bounded waiting duplicates, and stores a completed downstream response for replay. Every completed response is offered to a `ProxyEventPublisher`; the default publisher is a no-op, while Kafka is an explicit opt-in.

## State ownership

| State | Owner | Reason |
| --- | --- | --- |
| Routes and user accounts | PostgreSQL | Durable, relational configuration. |
| Database schema | Flyway | Explicit and repeatable migrations; Hibernate only validates. |
| Rate-limit counters and buckets | Redis | Shared, low-latency atomic state across application instances. |
| Idempotency leases and cached responses | Redis | Shared duplicate coordination with TTL-bound records. |
| Circuit state | Application memory | Small per-instance state machine, easy to test; resets on restart. |
| Completion events | Kafka, when enabled | Asynchronous, noncritical audit/analytics signal. |

## HTTP and security boundaries

All public APIs use `/api/v1`. Controllers use immutable DTOs; JPA entities never cross the HTTP boundary. Bean Validation rejects malformed route configuration, and route lists are paginated.

The security model is stateless HMAC-SHA256 Bearer JWTs. JWT signing material and the bootstrap administrator come from environment variables. `ADMIN` controls route mutations; `ADMIN`, `OPERATOR`, and `VIEWER` can read routes and proxy traffic. Errors have a stable JSON shape and a request ID.

The proxy strips hop-by-hop HTTP headers and forwards the request ID. Each route supplies an explicit read timeout; the proxy also has an explicit connection timeout. It preserves downstream HTTP status, body, and end-to-end headers.

## Reliability controls and limits

Redis Lua scripts make both rate-limiting algorithms atomic. Retry policy favors safe repeatability: idempotent methods may retry transient failures and 5xx responses; mutations need explicit opt-in for network-failure retries and are never retried on 5xx by this policy. The circuit breaker acts on the result of the retry loop, not every individual attempt.

Bounded workers and a bounded priority queue reject excess work with `503 PROXY_OVERLOADED`, rather than allowing an unlimited queue. The endpoint remains synchronous after admission. Idempotency improves duplicate-write safety but does not claim exactly-once downstream execution across every crash scenario. Kafka events are best effort and do not use an outbox.

## Possible future developments

Examples of optional extensions are measured load testing, operational metrics, stronger outbound-network controls, a connection pool if measurements justify it, broader test coverage, delivery automation, and a durable outbox if event-delivery guarantees become a requirement. None of these is required for the current architecture.
