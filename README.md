# Aegis

Aegis is a Java 21 / Spring Boot platform being built to manage API traffic, downstream failures, and operational visibility. It will eventually sit between API clients and configured downstream services; it is deliberately being built as an interview-quality modular monolith before introducing distributed infrastructure.

## Current status

**Phases 0–10 are complete.** The application forwards HTTP traffic with explicit per-route timeouts, Redis-backed distributed rate limiting, duplicate-request coordination, idempotency-aware retries, per-route circuit breaking, bounded priority execution, and optional Kafka proxy events.

Implemented:

- Java 21, Maven, Spring Boot, Spring Web, Spring Security, Spring Data JPA, and Bean Validation
- PostgreSQL persistence owned by Flyway (`ddl-auto=validate`)
- Route CRUD API at `/api/v1/routes`, including pagination, validation, and per-route rate limit configuration
- HMAC-signed JWT issuance with BCrypt password verification
- `ADMIN`, `OPERATOR`, and `VIEWER` role enforcement for route and proxy APIs
- Structured JSON errors and `X-Request-Id` correlation
- Actuator health endpoint (monitoring PostgreSQL and Redis) and OpenAPI/Swagger UI
- Controlled downstream HTTP proxy at `/api/v1/proxy/{routeName}/**` with per-route explicit timeouts
- Hop-by-hop header stripping and `X-Request-Id` forwarding on proxy calls
- Structured error codes for disabled routes (`ROUTE_DISABLED`) and downstream failures (`DOWNSTREAM_ERROR`)
- Redis connectivity via Spring Data Redis (Lettuce), `StringRedisTemplate`, and Actuator health check integration
- Redis-backed distributed rate limiting (`com.aegis.ratelimit`) supporting atomic `FIXED_WINDOW` and `TOKEN_BUCKET` algorithms via Redis Lua scripts
- Per-route rate limit configuration (`rateLimitAlgorithm`, `rateLimitCapacity`, `rateLimitWindowSeconds`, `rateLimitRefillRate`) with configurable application defaults
- Structured `429 RATE_LIMIT_EXCEEDED` error responses returning `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` HTTP headers
- Configured retries for idempotent methods; non-idempotent network retries require an explicit route opt-in
- Per-route `CLOSED` / `OPEN` / `HALF_OPEN` circuit breaker that rejects protected downstream traffic with `503 CIRCUIT_OPEN`
- Bounded worker pool and priority queue (`CRITICAL` through `LOW`) with immediate `503 PROXY_OVERLOADED` rejection when full
- Redis-backed `Idempotency-Key` coordination for `POST` and `PATCH` proxy requests, scoped to the authenticated caller and route
- Cached replay of completed downstream responses and bounded waiting for concurrent duplicates
- Optional, best-effort Kafka `aegis.proxy-events.v1` events for completed proxy responses; Kafka delivery does not alter HTTP outcomes
- Unit, controller-slice, deterministic concurrency, and PostgreSQL/Redis Testcontainers integration tests

Not implemented yet: Prometheus/Grafana, expanded test coverage, measured load tests, container image packaging, and CI.

## Requirements

- Java 21
- Maven 3.9+ (or the included `./mvnw`)
- Docker, for local PostgreSQL/Redis and Testcontainers-backed integration tests

## Local setup

```bash
cp .env.example .env
docker compose up -d postgres redis
./mvnw verify
./mvnw spring-boot:run
```

The application reads configuration from environment variables. `.env.example` contains local-development values; copy it to `.env` and do not commit real credentials.

`./mvnw test` runs the unit, MVC, and deterministic concurrency tests. `./mvnw verify` additionally runs the PostgreSQL and Redis Testcontainers integration tests.

Useful endpoints:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/v1/status
GET http://localhost:8080/v3/api-docs
GET http://localhost:8080/swagger-ui.html
```

## Delivery roadmap

The future design is intentionally phased so each increment stays runnable, testable, and explainable.

| Phase | Focus | Status |
| --- | --- | --- |
| 0 | Foundation: Spring Boot, PostgreSQL, Flyway, health checks | Complete |
| 1 | Route management: configuration CRUD, validation, OpenAPI | Complete |
| 2 | JWT authentication and ADMIN/OPERATOR/VIEWER authorization | Complete |
| 3 | Controlled downstream HTTP proxy with timeouts | Complete |
| 4 | Redis connectivity and health checks | Complete |
| 5 | Distributed fixed-window then token-bucket rate limiting | Complete |
| 6 | Configured, idempotency-aware retries | Complete |
| 7 | Testable circuit breaker | Complete |
| 8 | Bounded concurrency, priority, and backpressure | Complete |
| 9 | Idempotency keys and duplicate-request coordination | Complete |
| 10 | Kafka events for audit and analytics | Complete |
| 11 | Micrometer, Prometheus, Grafana, and structured operational metrics | Planned |
| 12 | Broader unit, integration, API, and concurrency coverage | Planned |
| 13 | Measured load testing | Planned |
| 14 | Local production-like Docker environment | Planned |
| 15 | CI/CD | Planned |

No performance or scalability figures are claimed until a documented load test produces them.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — implemented architecture, future target, and phased evolution
- [API.md](API.md) — implemented HTTP contract and planned API boundaries
- [DECISIONS.md](DECISIONS.md) — meaningful engineering decisions and trade-offs
- [LOAD_TESTING.md](LOAD_TESTING.md) — load-test plan and measured-results policy
- [AGENTS.md](AGENTS.md) — repository contribution rules for coding agents
- [HELP.md](HELP.md) — development references and project conventions
