# Aegis

Aegis is a Java 21 / Spring Boot platform being built to manage API traffic, downstream failures, and operational visibility. It will eventually sit between API clients and configured downstream services; it is deliberately being built as an interview-quality modular monolith before introducing distributed infrastructure.

## Current status

**Phase 0 (foundation) and Phase 1 (route management) are complete.** The application currently provides durable downstream-route configuration. It is not yet a proxy or a complete API gateway.

Implemented:

- Java 21, Maven, Spring Boot, Spring Web, Spring Security, Spring Data JPA, and Bean Validation
- PostgreSQL persistence owned by Flyway (`ddl-auto=validate`)
- Route CRUD API at `/api/v1/routes`, including pagination and validation
- Structured JSON errors and `X-Request-Id` correlation
- Actuator health endpoint and OpenAPI/Swagger UI
- Unit, controller-slice, and PostgreSQL Testcontainers integration tests

Not implemented yet: JWT authentication and role enforcement, request proxying, Redis, rate limiting, retries, circuit breaking, bounded request execution, idempotency, Kafka, Prometheus/Grafana, load tests, container image packaging, and CI.

## Requirements

- Java 21
- Maven 3.9+ (or the included `./mvnw`)
- Docker, for local PostgreSQL and Testcontainers-backed integration tests

## Local setup

```bash
cp .env.example .env
docker compose up -d postgres
./mvnw verify
./mvnw spring-boot:run
```

The application reads configuration from environment variables. `.env.example` contains local-development values; copy it to `.env` and do not commit real credentials.

`./mvnw test` runs the unit and MVC tests. `./mvnw verify` additionally runs the PostgreSQL Testcontainers integration tests.

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
| 2 | JWT authentication and ADMIN/OPERATOR/VIEWER authorization | Planned |
| 3 | Controlled downstream HTTP proxy with timeouts | Planned |
| 4 | Redis connectivity and health checks | Planned |
| 5 | Distributed fixed-window then token-bucket rate limiting | Planned |
| 6 | Configured, idempotency-aware retries | Planned |
| 7 | Testable circuit breaker | Planned |
| 8 | Bounded concurrency, priority, and backpressure | Planned |
| 9 | Idempotency keys and duplicate-request coordination | Planned |
| 10 | Kafka events for audit and analytics | Planned |
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
