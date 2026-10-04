# Aegis: interview guide

## The short version

Aegis is a Java/Spring Boot API traffic-management service. A client sends a request to Aegis, Aegis authenticates it, looks up a configured downstream route, applies traffic and reliability controls, and then forwards the request to that downstream service. It returns the downstream response to the caller while adding consistent error handling and a request ID for tracing.

The project is intentionally a **modular monolith**: one deployable application with clear packages for authentication, routes, proxying, rate limiting, retries, circuit breaking, idempotency, scheduling, and events. That keeps the system easy to run and reason about while retaining clean seams for later change.

## What problem it solves

Calling downstream services directly makes every client responsible for concerns such as authentication, timeouts, overload, duplicate submissions, and temporary downstream failures. Aegis centralizes those concerns at a controlled boundary.

For example, an `orders-service` route can be configured with its base URL, read timeout, priority, rate-limit policy, and retry policy. A client then calls Aegis at `/api/v1/proxy/orders-service/...` rather than calling the service directly. Aegis consistently enforces the route's rules before it forwards anything.

## Request journey

```text
Client request
  -> request ID (reuse or create X-Request-Id)
  -> JWT authentication and role authorization
  -> route lookup and enabled-state check
  -> Redis distributed rate-limit decision
  -> idempotency coordination for POST/PATCH when a key is supplied
  -> bounded, priority-aware proxy admission
  -> circuit-breaker permission
  -> downstream HTTP call with configured timeout and safe retries
  -> response, cached idempotency result, and optional Kafka completion event
```

Not every request uses every control. For example, idempotency applies only to `POST` and `PATCH` requests carrying `Idempotency-Key`; Kafka events are disabled unless explicitly enabled.

## Main capabilities and how to explain them

### Route management

Administrators manage durable downstream configuration through `/api/v1/routes`. A route has a unique name, absolute HTTP(S) base URL, timeout, enabled flag, execution priority, rate-limit settings, and retry settings.

The configuration lives in PostgreSQL. Flyway migrations own schema changes and Hibernate validates the schema at startup. Controllers use request and response DTOs instead of exposing JPA entities, so the persistence model is not accidentally made part of the public API.

### Authentication and authorization

The token endpoint verifies a BCrypt password and issues an HMAC-SHA256 JWT. The API is stateless: clients send the token as a Bearer token on each protected request. `ADMIN` can modify routes, while `ADMIN`, `OPERATOR`, and `VIEWER` can read routes and proxy traffic.

Secrets, token lifetime, and bootstrap administrator credentials come from environment variables. They are not hard-coded or logged.

### Controlled proxying

The proxy endpoint accepts any HTTP method under `/api/v1/proxy/{routeName}/**`. It resolves the route, appends the remaining path and query string to the configured base URL, and sends the downstream request with explicit connection and read timeouts.

Aegis forwards the downstream status, body, and end-to-end headers, but strips HTTP hop-by-hop headers such as `Connection`, `Host`, and `Transfer-Encoding`. This prevents headers that are meaningful only for one network hop from being incorrectly passed to the next one. It also forwards `X-Request-Id` for correlation across services.

### Distributed rate limiting

Rate limits are evaluated in Redis, so multiple Aegis instances can share the same limit rather than each enforcing a separate in-memory counter. Routes can use either:

- **Fixed window:** count requests in a defined time window.
- **Token bucket:** gradually refill a pool of tokens and allow a request only when a token is available.

The update and decision run as small Redis Lua scripts. That is important because a client-side read, calculation, and write could race when requests reach different application instances. Rejected requests receive `429 RATE_LIMIT_EXCEEDED`, rate-limit headers, and `Retry-After`.

### Safe retries and circuit breaking

Retries are deliberately conservative. Idempotent HTTP methods are eligible by default; retries for mutating methods need explicit route opt-in because an uncertain outcome can otherwise duplicate a side effect. The circuit breaker observes the final logical outcome after retries rather than counting every retry attempt as a separate client failure.

Each route has a circuit breaker with `CLOSED`, `OPEN`, and `HALF_OPEN` states. Repeated failures open the circuit and return `503 CIRCUIT_OPEN` for a configured period. A limited probe later tests recovery. This stops Aegis from continuously adding pressure to an unhealthy downstream service.

### Bounded concurrency and priority

Downstream work is admitted through a fixed-size worker pool and bounded priority queue. When both workers and queue are full, Aegis immediately returns `503 PROXY_OVERLOADED` rather than allowing unlimited waiting work to consume memory and worsen an outage.

Routes have priorities from `CRITICAL` to `LOW`. Priority affects queued work only; work already running is never preempted. This is a practical, deliberately synchronous design rather than an attempt to introduce a broad asynchronous platform.

### Idempotency for duplicate writes

For `POST` and `PATCH`, a client can provide `Idempotency-Key`. Aegis scopes it to the authenticated caller and route and binds it to a fingerprint of the method, path, query string, and body.

Redis atomically grants one request a short processing lease. A matching concurrent duplicate waits for a bounded time and can replay the completed downstream response. Reuse of the same key for different content returns `409 IDEMPOTENCY_KEY_REUSED`. The implementation improves safety for client retries, but it does **not** claim universal exactly-once execution: a crash after the downstream side effect but before Aegis records completion remains an uncertain-outcome case.

### Optional Kafka events

When explicitly enabled, Aegis publishes a small `ProxyCompletedEvent` after a proxy response completes. The event contains non-sensitive metadata such as route, method, status, time, and whether a response was replayed. Kafka delivery is asynchronous and best effort: an unavailable broker does not change the HTTP result. There is no durable outbox or exactly-once delivery claim.

## Errors and observability

Every structured JSON error includes a `requestId`. Common examples are `ROUTE_NOT_FOUND`, `RATE_LIMIT_EXCEEDED`, `CIRCUIT_OPEN`, `PROXY_OVERLOADED`, and `DOWNSTREAM_ERROR`. This gives API consumers stable error categories and gives operators a correlation value for logs.

Spring Boot Actuator exposes health information, including PostgreSQL and Redis connectivity. OpenAPI and Swagger UI document the HTTP contract.

## Key engineering choices

| Choice | Why it fits Aegis |
| --- | --- |
| Modular monolith | One request pipeline and one deployment keep the project understandable without losing module boundaries. |
| PostgreSQL + Flyway | Route and user data are durable, relational configuration; migrations make changes reviewable and repeatable. |
| Redis + Lua | Shared, short-lived coordination needs atomic decisions across instances. |
| `RestClient` with JDK HTTP infrastructure | A lightweight Spring-native proxy client with explicit timeouts; pooling is deferred until measured need. |
| Bounded execution | Prefer predictable rejection under overload to unbounded memory growth. |
| Best-effort Kafka events | Analytics/audit signals should not become a synchronous availability dependency. |

## An interview-ready answer

> I built Aegis as a Java 21 Spring Boot modular monolith that acts as a controlled gateway to configured downstream services. It stores routes and users in PostgreSQL, authenticates callers with JWTs, and forwards requests with explicit timeouts and HTTP-safe header handling. Before forwarding, it can enforce Redis-backed distributed rate limits, bound and prioritize downstream work, protect unhealthy services with circuit breakers, and coordinate idempotent write retries with Redis. I kept the failure modes explicit: clients get structured errors and a request ID, and optional Kafka completion events never affect the synchronous response. The main trade-off is that it is deliberately one deployable service with a synchronous proxy path; that made correctness and technical discussion the priority over premature distributed complexity.

## Honest limitations and possible future developments

Aegis deliberately avoids unmeasured claims about throughput, latency, availability, or scalability. Its HTTP client configuration is intentionally simple, and its bounded synchronous execution settings require deployment-specific tuning and load testing.

Potential future developments include production metrics dashboards, broader automated and load-test coverage, container/CI delivery, connection-pool changes informed by measurement, a durable outbox for stronger event delivery, and more advanced outbound-network controls. These are opportunities, not prerequisites for understanding or presenting the current project.
