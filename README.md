# Aegis

Aegis is a Java 21 and Spring Boot API traffic-management service. It sits in front of configured downstream HTTP services and provides a controlled request path: JWT authorization, route configuration, explicit timeouts, distributed rate limits, retry safety, circuit breaking, bounded priority execution, idempotency coordination, and optional Kafka completion events.

It is an interview-quality **modular monolith**. The code is one deployable application, with clear modules for each concern rather than premature microservices.

## What is implemented

- PostgreSQL-backed route and user configuration, managed by Flyway; Hibernate validates the schema.
- JWT authentication with BCrypt password verification and `ADMIN`, `OPERATOR`, and `VIEWER` roles.
- Versioned route CRUD with validation, pagination, OpenAPI, and Swagger UI.
- A controlled HTTP proxy with per-route timeouts, request-ID propagation, hop-by-hop header removal, and structured errors.
- Redis-backed fixed-window and token-bucket rate limiting using atomic Lua scripts.
- Idempotency-aware retry policies, per-route in-memory circuit breaking, and bounded priority proxy execution.
- Redis-backed idempotency keys for POST/PATCH duplicate coordination and completed-response replay.
- Optional best-effort Kafka events for completed proxy responses.
- Unit, MVC, deterministic concurrency, and PostgreSQL/Redis Testcontainers integration tests.

The project makes no unmeasured throughput, latency, availability, or capacity claims.

## Run locally

Requirements: Java 21, Docker, and either Maven 3.9+ or the included Maven wrapper.

```bash
cp .env.example .env
docker compose up -d postgres redis
./mvnw verify
./mvnw spring-boot:run
```

`.env.example` contains local-development values only. Copy it to `.env` and replace placeholders; never commit real credentials or signing material.

Useful endpoints:

```text
GET  http://localhost:8080/actuator/health
GET  http://localhost:8080/api/v1/status
POST http://localhost:8080/api/v1/auth/token
GET  http://localhost:8080/v3/api-docs
GET  http://localhost:8080/swagger-ui.html
```

`./mvnw test` runs the unit and MVC tests. `./mvnw verify` also runs the Testcontainers integration tests.

## End-to-end demonstration

The repository includes a small, standalone Orders service and Compose stack under [`demo/`](demo/README.md). It makes Aegis's intended deployment boundary visible: clients call Aegis, while Aegis applies traffic controls and forwards requests to an independent HTTP service. It is a local showcase only; the demo service is not packaged into Aegis.

## Documentation

- [INTERVIEW_GUIDE.md](INTERVIEW_GUIDE.md) — a plain-language explanation and interview narrative.
- [CODE_WALKTHROUGH.md](CODE_WALKTHROUGH.md) — a detailed source-level guide to the implementation.
- [ARCHITECTURE.md](ARCHITECTURE.md) — implemented architecture and current trade-offs.
- [API.md](API.md) — HTTP contract.
- [DECISIONS.md](DECISIONS.md) — engineering decisions and their trade-offs.

## Possible future developments

The current project is complete enough to present on its own. If requirements justify it, future work could add measured load testing, operational metrics and dashboards, broader test coverage, CI/container delivery, stronger outbound-network controls, connection-pooling changes informed by measurement, or a durable event outbox. These are optional enhancements, not promised delivery phases.
