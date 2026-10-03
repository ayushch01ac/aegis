# Decisions

## D1. Modular monolith first

**Decision:** Ship Aegis as one Spring Boot application with logical package boundaries.

**Reason:** The product is still one request pipeline. Separate services would add deployment and consistency costs before a scaling or ownership need exists.

**Alternative:** Start with a service per reliability concern.

**Trade-off:** A future extraction requires a process boundary, but package boundaries preserve that option.

## D2. Flyway owns schema; Hibernate validates

**Decision:** Use Flyway migrations and `spring.jpa.hibernate.ddl-auto=validate`.

**Reason:** Production schema changes must be explicit, reviewable, and reproducible. Auto-DDL can hide incompatibilities.

**Alternative:** Liquibase; Hibernate `update` for development.

**Trade-off:** Even small schema changes need a migration.

## D3. PostgreSQL now; Redis and Kafka when their state model is needed

**Decision:** Docker Compose currently runs PostgreSQL only. Routes are stored in PostgreSQL; Redis and Kafka are deferred.

**Reason:** Route definitions are durable, infrequently changed configuration. Redis is for shared short-lived state and Kafka for asynchronous events, neither of which the current API requires.

**Alternative:** Start the full future infrastructure stack immediately.

**Trade-off:** The local topology is intentionally incomplete until the matching phases.

## D4. Permit-all security is a temporary phase boundary

**Decision:** Keep Spring Security on the classpath, disable CSRF/form/basic login, use stateless sessions, and permit all requests until the authentication phase.

**Reason:** Route-management APIs need to remain usable while JWT configuration, secret management, users, and role rules are not yet implemented.

**Alternative:** Remove Spring Security until later or block all routes by default.

**Trade-off:** The current application is not protected. Phase 2 must replace this filter chain and add authorization tests.

## D5. Route entities remain internal to the route module

**Decision:** Controllers use request/response DTOs rather than exposing the JPA `Route` entity.

**Reason:** The HTTP contract can evolve independently of persistence and validation stays explicit at the boundary.

**Alternative:** Return entities directly.

**Trade-off:** Mapping code is required, but the API is safer to change.

## D6. Route URLs are syntactically validated now; proxy safety is deferred

**Decision:** Route creation accepts absolute `http` or `https` URLs with a host. It does not yet make outbound calls or enforce a network allowlist.

**Reason:** Phase 1 stores downstream configuration only. SSRF controls require proxy behavior and an explicit deployment policy.

**Alternative:** Attempt reachability checks at route creation.

**Trade-off:** Phase 3 must add outbound URL/network controls before forwarding traffic.

## D7. Documentation separates present behavior from planned design

**Decision:** Repository docs mark implemented capabilities separately from the target architecture and phased roadmap.

**Reason:** Aegis is intentionally incremental; presenting planned Redis, Kafka, or reliability features as complete would be misleading.

**Trade-off:** Documentation repeats the phase boundary in several places to keep the project explainable.

## D8. RestClient with SimpleClientHttpRequestFactory for the proxy

**Decision:** Use Spring's `RestClient` backed by `SimpleClientHttpRequestFactory` for downstream calls.

**Reason:** `RestClient` is the modern Spring replacement for `RestTemplate` and supports the `retrieve()` / `toEntity()` flow needed to pass status and headers through verbatim. `SimpleClientHttpRequestFactory` requires no additional dependencies; connection pooling and keep-alive behaviour are provided by the JDK. Phase 3 does not need to maximise throughput, only to enforce timeouts correctly.

**Alternative:** Apache HttpComponents `HttpAsyncClient` (non-blocking) or `HttpClient` (pooled, synchronous).

**Trade-off:** The JDK factory creates a new connection for each request in the default configuration, so throughput under sustained load is lower than a pooled client. A future phase (bounded concurrency or load testing) is the right place to introduce pooling with a demonstrated need.

## D9. Hop-by-hop headers are stripped before forwarding

**Decision:** Remove `Host`, `Connection`, `Keep-Alive`, `Proxy-Authorization`, `TE`, `Trailers`, `Transfer-Encoding`, and `Upgrade` before forwarding the client request to the downstream service.

**Reason:** These headers are meaningful only for the Aegis–client leg and must not be forwarded per HTTP/1.1 semantics (RFC 7230 §6.1). Forwarding them can confuse the downstream server or leak internal connection parameters.

**Alternative:** Forwarding all headers without filtering.

**Trade-off:** Application-level headers (e.g. `Authorization`, `Content-Type`) are still forwarded. A future phase may introduce an explicit allow-list if SSRF or header-injection concerns grow.
