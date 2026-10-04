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

## D3. PostgreSQL for relational persistence; Redis for short-lived state; Kafka only for opt-in events

**Decision:** Routes and user accounts are stored in PostgreSQL; Redis provides low-latency shared state. Kafka is limited to optional asynchronous event publication and is not a synchronous dependency.

**Reason:** Route definitions and user credentials are durable configuration suited for PostgreSQL. Redis provides the low-latency, key-value primitives required for rate-limiting (Phase 5) and idempotency coordination (Phase 9).

**Alternative:** Start the full future infrastructure stack (including Kafka and Prometheus) immediately.

**Trade-off:** Adds a Redis container requirement to local execution and Testcontainers integration tests.

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

**Alternative:** Forwarding all headers without filtering.

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

## D10. Spring Data Redis with Lettuce for shared state and Actuator health check

**Decision:** Add `spring-boot-starter-data-redis` using Lettuce as the connection driver, expose `StringRedisTemplate`, and monitor connection status via Spring Boot Actuator's `RedisHealthIndicator`.

**Reason:** Lettuce is Spring Boot's default non-blocking Redis driver. Exposing `StringRedisTemplate` provides a thread-safe, string-oriented interface ready for distributed rate limiting in Phase 5. Actuator integration ensures operational visibility into Redis health alongside PostgreSQL.

**Alternative:** Custom Jedis connection factory or delayed Redis bean setup until Phase 5.

**Trade-off:** Requires Redis configuration properties (`spring.data.redis.*` and `aegis.redis.*`) and running a Redis instance or Testcontainer for full integration testing.

## D11. Redis Lua scripts for atomic distributed fixed-window and token-bucket rate limiting

**Decision:** Implement fixed-window and token-bucket rate limiting using custom Redis Lua scripts via Spring's `StringRedisTemplate`.

**Reason:** Multi-step read-calculate-write rate limit updates in distributed environments suffer from race conditions. Executing logic in Redis Lua scripts guarantees atomic evaluation across horizontally scaled Aegis instances without requiring distributed locks.

**Alternative:** In-memory rate limiting (Guava/Bucket4j in-JVM) or Redis client-side distributed locks.

**Trade-off:** Lua scripts execute synchronously inside Redis; script complexity must remain minimal to avoid blocking the Redis event loop.

## D12. Conservative retries precede circuit-breaker accounting

**Decision:** Retry only idempotent methods by default, and let the circuit breaker observe the final outcome of the logical downstream call.

**Reason:** Retrying a mutation after an uncertain downstream result can duplicate side effects. Counting individual retry attempts would open a circuit too aggressively for a single client request.

**Trade-off:** A route can still opt in to network-failure retries for non-idempotent methods before idempotency-key support exists; that remains an explicit operational risk.

## D13. Bounded local priority execution protects downstream services

**Decision:** Execute proxy work through a fixed-size worker pool with a bounded priority queue, ordered `CRITICAL`, `HIGH`, `NORMAL`, then `LOW`.

**Reason:** The proxy must reject excess work predictably instead of accumulating requests until memory pressure or downstream collapse. Priority is applied only to queued work; work already running is not preempted.

**Trade-off:** A waiting servlet thread still waits for its admitted task. This is a deliberately small synchronous design; queue and worker limits must be tuned to the deployment and should be supported by measured load tests before changing defaults.

## D14. Redis coordinates idempotency keys and caches completed proxy responses

**Decision:** For `POST` and `PATCH` proxy calls that send `Idempotency-Key`, use Redis Lua scripts to atomically create a processing lease, bind the key to a request fingerprint, and retain the completed status, headers, and body for replay.

**Reason:** The same key must not permit concurrent downstream side effects across application instances. Redis already provides the low-latency shared state required for the atomic ownership transition.

**Alternative:** Rely on client retries, use in-memory locks, or create a durable relational idempotency table.

**Trade-off:** The design is bounded by Redis record TTLs and does not claim universal exactly-once execution. A completed downstream response is replayable, but a worker crash after downstream execution and before completion is still an uncertain-outcome case.

## D15. Kafka proxy events are opt-in and best effort

**Decision:** Publish non-sensitive `ProxyCompletedEvent` records asynchronously only when Kafka is explicitly enabled. Do not wait for delivery or make the HTTP response depend on it.

**Reason:** Audit and analytics must not become a new availability dependency in the synchronous proxy path.

**Alternative:** Synchronous broker acknowledgement or a transactional outbox in the request transaction.

**Trade-off:** Delivery can be lost when Kafka is unavailable. The phase intentionally defers durable outbox semantics, consumers, and exactly-once processing until a concrete business requirement justifies them.
