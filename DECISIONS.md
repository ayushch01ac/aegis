# Engineering decisions

## D1. Modular monolith

**Decision:** Keep Aegis as one Spring Boot application with focused package boundaries.

**Why:** It has one coherent request pipeline. Separate services would add network, deployment, and consistency costs without a demonstrated ownership or scaling benefit.

**Trade-off:** A later extraction would require a process boundary, but the module boundaries preserve that option.

## D2. Flyway owns schema changes

**Decision:** Use Flyway migrations and Hibernate `ddl-auto=validate`.

**Why:** Schema changes are explicit, reviewable, reproducible, and safe to verify at startup.

**Trade-off:** Even a small persistence change needs a migration.

## D3. PostgreSQL for durable configuration; Redis for short-lived coordination

**Decision:** Store routes and user accounts in PostgreSQL; use Redis for rate limits and idempotency state.

**Why:** The data has different lifetimes and access patterns. Redis provides atomic, TTL-bound shared state without making it the source of durable configuration.

**Trade-off:** Local full-stack execution and integration tests need PostgreSQL and Redis.

## D4. Stateless JWT security

**Decision:** Use HMAC-SHA256 Bearer JWTs with BCrypt password verification and role authorization.

**Why:** The API stays stateless while retaining a small, locally managed access model.

**Trade-off:** Signing-key rotation and multi-identity-provider support are not implemented.

## D5. DTOs at the HTTP boundary

**Decision:** Keep JPA entities internal and expose immutable request/response records.

**Why:** The API contract can evolve independently from persistence; validation remains explicit at the boundary.

**Trade-off:** Mapping code is intentional overhead.

## D6. RestClient with per-call timeout configuration

**Decision:** Use Spring `RestClient` with a per-call `SimpleClientHttpRequestFactory`.

**Why:** Every route can enforce its own read timeout while retaining an explicit connection timeout without shared mutable client state.

**Trade-off:** This simple client arrangement is not optimized for sustained pooled-connection throughput; any change should be measurement-driven.

## D7. Strip hop-by-hop proxy headers

**Decision:** Do not forward `Host`, `Connection`, `Keep-Alive`, `Proxy-Authorization`, `TE`, `Trailers`, `Transfer-Encoding`, or `Upgrade`.

**Why:** These headers apply to one network connection and can be invalid or misleading on the downstream connection.

**Trade-off:** Application-level headers still flow through; an explicit header allow-list is not currently used.

## D8. Redis Lua for distributed rate limits

**Decision:** Implement fixed-window and token-bucket decisions in small Redis Lua scripts.

**Why:** The scripts make each read/update decision atomic across multiple Aegis instances without distributed locks.

**Trade-off:** Scripts run synchronously in Redis and must remain short.

## D9. Conservative retries and final-outcome circuit accounting

**Decision:** Retry idempotent methods by default and let the circuit breaker observe the final result of a logical request.

**Why:** Retrying mutations after uncertain outcomes may duplicate effects; counting each retry as a separate failure would open circuits too aggressively.

**Trade-off:** Optional network-retry opt-in for mutations carries an explicit operational risk.

## D10. Bounded priority execution

**Decision:** Use a fixed worker pool with a bounded priority queue.

**Why:** Predictable overload rejection is safer than unbounded waiting work during a downstream incident.

**Trade-off:** The caller's servlet thread waits after admission, and real deployment settings need measured tuning.

## D11. Redis-backed idempotency with bounded claims

**Decision:** Coordinate eligible write requests with a Redis processing lease and cache completed responses for replay.

**Why:** It prevents normal concurrent duplicate work across application instances and makes client retry behavior safer.

**Trade-off:** TTLs and crash windows mean it is not an exactly-once protocol.

## D12. Kafka events are opt-in and best effort

**Decision:** Publish non-sensitive completion events asynchronously only when enabled.

**Why:** Audit/analytics signals must not become a synchronous dependency of the proxy response.

**Trade-off:** Without an outbox, events can be lost when Kafka is unavailable.
