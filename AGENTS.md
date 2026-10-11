# Contribution notes

Aegis is a Java 21, Spring Boot, Maven modular monolith for controlled API traffic. Keep changes small, focused, and consistent with the implemented architecture.

Before changing code, inspect the relevant module, configuration, Flyway migration, tests, and the architecture documentation. Use constructor injection, immutable HTTP DTOs, Bean Validation, paginated unbounded lists, `/api/v1` APIs, structured errors with request IDs, and environment-based secrets. Never expose JPA entities from controllers or log credentials, tokens, secrets, or sensitive bodies.

After changing code, compile, run focused tests, and run `./mvnw verify` when practical. Update the documentation when behavior, API contract, or an engineering trade-off changes. Do not invent performance figures.

Potential future developments are optional and must not be presented as existing behavior without implementation, tests, and documentation.
