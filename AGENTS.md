# Agent instructions

## Mission and current position

Aegis is an interview-quality Java backend project for API reliability and traffic management. Build it incrementally for clarity, correctness, and technical discussion—not to add every technology at once.

Phase 0 (foundation) and Phase 1 (route management) are complete. The next planned phase is JWT authentication and role authorization. `README.md`, `ARCHITECTURE.md`, `API.md`, and `DECISIONS.md` are the repository's record of actual implementation; future architecture is a plan, not permission to implement unrelated phases.

## Before changing code

1. Inspect the repository, `README.md`, and `ARCHITECTURE.md`.
2. Inspect the relevant module boundary, configuration, migration, and tests.
3. Identify what is already implemented and preserve working behavior.
4. Make the smallest coherent change for the active phase.
5. Explain a material trade-off before introducing complexity beyond the active phase.

## After changing code

1. Compile.
2. Run focused tests, then `./mvnw verify` when practical (`./mvnw test` runs the unit-test phase only).
3. Update `README.md`, `ARCHITECTURE.md`, `API.md`, `DECISIONS.md`, and `LOAD_TESTING.md` when their subject changes.
4. Summarize changed files, verification performed, and known limitations.

## Rules

- Use Java 21, Spring Boot, and Maven.
- Keep a modular monolith. Do not split microservices without a concrete scaling or ownership reason.
- Prefer constructor injection, focused classes, immutable HTTP DTOs, and explicit configuration.
- Do not expose JPA entities from controllers.
- Use versioned APIs under `/api/v1`; return consistent structured errors with a request ID.
- Use Bean Validation at the HTTP boundary and pagination for unbounded collections.
- Use Flyway migrations. Hibernate validates production schema; it must not create it.
- Keep secrets out of git and configure them through environment variables. Never hardcode passwords, JWT secrets, or credentials.
- Do not log passwords, JWTs, secrets, or sensitive request bodies.
- Do not invent throughput, latency, availability, or scalability figures.
- Do not introduce Redis, Kafka, retries, circuit breakers, bounded executors, or custom thread pools before their planned phase demonstrates a concrete need.
- Prefer deterministic concurrency tests using latches, barriers, futures, or bounded polling—not arbitrary sleeps.
- Do not add Lombok, generic frameworks, factories, or abstractions unless they solve a demonstrated problem.

## Planned order

1. Authentication and authorization
2. Basic controlled proxy with explicit timeouts
3. Redis integration, then distributed rate limiting
4. Retry and circuit breaker
5. Bounded concurrency and backpressure
6. Idempotency
7. Kafka events, observability, expanded testing, load testing, Compose, and CI

When a request conflicts with this order, keep the current system stable and propose the smallest safe path.
