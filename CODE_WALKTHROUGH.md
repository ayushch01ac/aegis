# Aegis: code walkthrough

This guide explains the implementation from the code outward. Read [INTERVIEW_GUIDE.md](INTERVIEW_GUIDE.md) first for the product-level story.

## 1. Application shape

`com.aegis.AegisApplication` starts one Spring Boot application. It is organized as a modular monolith:

```text
common       HTTP configuration, request IDs, errors, pagination, status
auth         users, password verification, JWT issue/validation, authorization
route        persistent downstream-route configuration
proxy        HTTP request forwarding and pipeline orchestration
ratelimit    Redis Lua limiters and policy resolution
retry        retry policy and executor
circuitbreaker per-route downstream health state
scheduling   bounded priority execution
idempotency  Redis duplicate coordination and response replay
messaging    optional Kafka proxy-completion publication
```

The packages are not separate deployables. They are boundaries inside one application, which keeps the call path direct and makes local development straightforward.

## 2. Startup and configuration

`src/main/resources/application.yaml` is the configuration source. It wires PostgreSQL, Redis, Kafka producer settings, Actuator, OpenAPI, and application-specific `aegis.*` properties. Environment variables override the safe local defaults; `.env.example` lists the required local values without containing a real secret.

Several records use `@ConfigurationProperties` with Bean Validation, including `ProxyProperties`, `RateLimitProperties`, `RetryProperties`, `CircuitBreakerProperties`, `ExecutionProperties`, `IdempotencyProperties`, and `MessagingProperties`. That means invalid operational values fail early instead of creating surprising runtime behavior.

Persistence is deliberately migration-first:

- `V1__create_routes.sql` creates route configuration and its unique name index.
- `V2__create_user_accounts.sql` creates local users.
- `V3__add_rate_limiting_to_routes.sql` adds route-level rate-limit overrides.
- `V4__add_retry_to_routes.sql` adds route-level retry overrides.

Flyway applies migrations, while `spring.jpa.hibernate.ddl-auto=validate` asks Hibernate to verify—not invent—the resulting schema.

## 3. Common HTTP behavior

`RequestIdFilter` runs at the highest filter precedence. It accepts an incoming `X-Request-Id` if nonblank and at most 128 characters; otherwise it generates a UUID. It places the ID in SLF4J's MDC for logs, echoes it in the response, and always removes it in `finally` to avoid leaking values between reused threads.

`GlobalExceptionHandler` maps domain exceptions and Spring MVC errors into one `ApiError` shape. Individual domain exceptions extend `AegisException`, which carries HTTP status, stable application code, message, and optional response headers. This is why a rate-limit rejection can return both a JSON error and `Retry-After`.

Security failures occur in the Spring Security filter chain before MVC reaches the global exception handler. `SecurityErrorWriter` therefore writes the same JSON shape for `401 UNAUTHORIZED` and `403 ACCESS_DENIED`.

`WebConfig` and `ApiProperties` constrain pagination defaults and maximum page size. `StatusController` provides a simple public status endpoint; Actuator supplies dependency health.

## 4. Authentication and authorization

The authentication flow is intentionally local and stateless:

```text
POST /api/v1/auth/token
  -> AuthController
  -> AuthService loads UserAccount by username
  -> BCrypt PasswordEncoder checks password hash
  -> JwtTokenService creates HS256 JWT with subject + roles claim
  -> client sends Authorization: Bearer <token> on later calls
```

`BootstrapAdminInitializer` creates one `ADMIN` account only if the configured username is absent. The password is BCrypt-hashed before persistence, and the code logs the username but never the password.

`JwtConfiguration` decodes the Base64 secret, rejects keys shorter than 32 bytes, and builds matching HMAC encoder and decoder beans. `SecurityConfig` converts the JWT `roles` claim into Spring `ROLE_*` authorities. Its rules are deliberate:

- Health, status, OpenAPI, Swagger UI, and token issuance are public.
- Route reads and proxy calls accept `ADMIN`, `OPERATOR`, or `VIEWER`.
- Route creation, update, and deletion require `ADMIN`.
- CSRF, HTTP Basic, form login, and server sessions are disabled because this is a Bearer-token API.

## 5. Route configuration

`Route` is the JPA entity, intentionally internal to the `route` package. It contains both proxy basics (name, URL, timeout, priority, enabled) and optional per-route overrides for rate limiting and retries. Its persistence callbacks create UUIDs and maintain timestamps.

The public contract is immutable records:

- `RouteRequest` validates the incoming HTTP body, including `@HttpUrl`, timeout bounds, priority, and policy limits.
- `RouteResponse` maps the entity to an API response.
- `RouteController` exposes create, paginated list, get, update, and delete operations.
- `RouteService` owns transactions, uniqueness checks, mapping, and domain exceptions.
- `RouteRepository` supplies ID and unique-name lookup.

`HttpUrlValidator` enforces syntactic HTTP(S) URLs with a host. It does not perform network reachability checks or IP/network allowlisting; those are distinct outbound-security concerns that require deployment policy.

## 6. The proxy path, line by line

The relevant entry point is `ProxyController` at `/api/v1/proxy/{routeName}/**`.

1. Spring Security verifies the Bearer token and role before controller execution.
2. The controller reads the request method, body, all incoming headers, remaining path, and raw query string.
3. It gets the request ID from MDC and makes sure the downstream header has the same value.
4. For `POST` and `PATCH` with a nonblank `Idempotency-Key`, it delegates the downstream operation to `IdempotencyService`; otherwise it directly calls `ProxyService`.
5. After a response—original or replayed—it calls `ProxyEventPublisher`. With Kafka disabled this is a no-op.
6. It returns the downstream status, headers, and bytes unchanged except for the additions/removals made by the pipeline.

`ProxyService` is the orchestration layer. It:

1. Retrieves the route by name or throws `ROUTE_NOT_FOUND`.
2. Rejects disabled routes with `503 ROUTE_DISABLED`.
3. Calls `RateLimitService`; rejection becomes `429 RATE_LIMIT_EXCEEDED`.
4. Builds the downstream URI from base URL, remaining path, and raw query string.
5. Resolves the route's retry policy, then submits the logical downstream call to `PriorityExecutionService`.
6. Inside the admitted task, lets `CircuitBreaker` allow or reject the call and asks `RetryExecutor` to invoke `DownstreamHttpClient`.
7. Adds the final rate-limit headers to a returned downstream response.

The nesting is important:

```text
priority admission
  -> circuit breaker
      -> retry executor
          -> downstream HTTP attempt
```

The circuit breaker sees the final outcome of the retry loop. A single client request that retries is therefore not counted as several independent downstream failures.

`DownstreamHttpClient` creates a `SimpleClientHttpRequestFactory` for each call. That lets it apply an explicit shared connection timeout plus the selected route read timeout without mutating a shared client. It forwards raw bytes and configures `RestClient` not to throw simply because the downstream returned a non-2xx status; downstream status and body can therefore pass through.

Before forwarding, it removes hop-by-hop headers: `Host`, `Connection`, `Keep-Alive`, `Proxy-Authorization`, `TE`, `Trailers`, `Transfer-Encoding`, and `Upgrade`. `ResourceAccessException` and other call failures become `DownstreamException`, eventually mapped to a structured gateway error.

## 7. Distributed rate limiting

`RateLimitService` resolves a route override first and falls back to `aegis.rate-limit` defaults. If disabled it permits all requests. If Redis fails, `failOpen` determines whether a request is allowed or the Redis failure is propagated; the default is fail closed.

`FixedWindowRateLimiter` creates a key containing route, window size, and time bucket. Its Lua script does `INCR` and sets expiry only on the first request. The script makes increment-and-expiry atomic; the Java code compares the resulting count with the configured limit and calculates the next-window retry time.

`TokenBucketRateLimiter` stores `tokens` and `last_refill_ms` in a Redis hash. Its Lua script refills based on elapsed time, caps at capacity, deducts one token when possible, applies a TTL, and returns allowed/remaining/retry-after in one atomic operation.

This design coordinates a limit across application instances, but its key is the route name—not an individual client identity. That is a meaningful policy choice: the documented limit protects each downstream route as a whole.

## 8. Retries and circuit breaking

`RetryPolicy` determines whether another attempt is safe:

- `GET`, `HEAD`, `OPTIONS`, and `PUT` retry network failures and 5xx responses.
- `POST`, `PATCH`, and `DELETE` do not retry by default.
- A route can opt in to retries of network failures for a non-idempotent method, but never to 5xx retries for those methods.
- 4xx responses never retry.

`RetryExecutor` owns the mutable attempt loop. It uses exponential backoff (`initial * multiplier^(attempt-1)`) with a 30-second cap. Its sleep is interruptible and restores the thread interrupt flag when interrupted.

`CircuitBreaker` keeps in-memory state per route in a `ConcurrentHashMap`; a small synchronized inner object protects state transitions. Consecutive 5xx responses or runtime call failures reach the configured threshold and move from `CLOSED` to `OPEN`. While open, it rejects calls with `CIRCUIT_OPEN`. Once its open duration passes, it allows a configured number of `HALF_OPEN` probes. A successful response closes and resets it; a failure reopens it.

Because the circuit map is in-process, its state is not shared by multiple application instances and disappears on restart. Redis is used for controls requiring cross-instance coordination; the circuit breaker currently prioritizes a small, locally testable implementation.

## 9. Bounded priority execution

`PriorityExecutionService` owns a fixed-size `ThreadPoolExecutor`. `BoundedPriorityBlockingQueue` wraps `PriorityBlockingQueue` with a `Semaphore`, because Java's built-in priority queue is otherwise unbounded.

Each work item is a `PrioritizedFutureTask`, ordered first by `RoutePriority` (`CRITICAL` ahead of `HIGH`, `NORMAL`, and `LOW`) and then by an increasing sequence number for FIFO behavior within a priority. An `AbortPolicy` refusal becomes `OverloadedException`, which maps to `503 PROXY_OVERLOADED` with `Retry-After: 1`.

The servlet thread calls `task.get()` after successful admission. So admission and queue order are bounded, but the HTTP endpoint remains synchronous. This is an intentional scope choice, not a claim of non-blocking end-to-end I/O.

## 10. Idempotency coordination

`IdempotencyService` is used only for eligible proxy writes. It hashes `scope + key` to form a Redis key, where scope combines route name and authenticated subject. It separately fingerprints the request's route, method, path, query string, and raw body.

Three Lua scripts manage the state machine:

```text
missing record --acquire--> PROCESSING (owner has short lease)
PROCESSING --complete--> COMPLETED (status + headers + body cached)
PROCESSING --release--> missing (gateway failure allows a later retry)
```

- A different fingerprint for the same scoped key returns `409 IDEMPOTENCY_KEY_REUSED`.
- A completed matching request reconstructs and returns the cached response.
- A matching request already in progress polls Redis only until a configured deadline, then returns `409 IDEMPOTENCY_IN_PROGRESS`.
- The completed record stores headers as JSON and body as Base64 for safe Redis string storage.

The approach prevents normal concurrent duplicates across Aegis instances, but it cannot guarantee exactly-once effects in a crash window after a downstream system performs the work but before Aegis persists `COMPLETED`.

## 11. Optional Kafka publication

`ProxyEventPublisher` has two conditional implementations:

- `NoOpProxyEventPublisher` is active by default.
- `KafkaProxyEventPublisher` is active only when `aegis.messaging.kafka.enabled=true`.

The Kafka implementation calls `KafkaTemplate.send(...)` and attaches an error callback. Submission, serialization, and broker errors are logged but swallowed so Kafka cannot alter the completed HTTP response. `ProxyCompletedEvent` intentionally excludes request bodies, response bodies, credentials, tokens, and query parameters.

## 12. Tests and what they prove

The test suite mirrors these module boundaries:

- MVC/controller tests check route, authentication, and proxy HTTP contracts.
- Unit tests cover URL validation, retry decisions/backoff, circuit transitions, priority admission, and both rate-limit algorithms.
- Concurrency tests use latches/barriers instead of arbitrary sleeps.
- `*IT` tests use Testcontainers for PostgreSQL and Redis, covering migrations/repositories, Redis health, distributed rate limiting, and idempotency coordination.

Run `./mvnw test` for unit/MVC tests and `./mvnw verify` for the complete verification lifecycle. Docker is needed when Testcontainers integration tests run.

## 13. Reading order for an interview or code review

1. `README.md` and `INTERVIEW_GUIDE.md` for scope and narrative.
2. `ProxyController`, then `ProxyService`, to trace one request.
3. `Route`/`RouteService` and migrations for configuration persistence.
4. `SecurityConfig`, `AuthService`, and `JwtTokenService` for access control.
5. `RateLimitService`, `RetryExecutor`, `CircuitBreaker`, `PriorityExecutionService`, and `IdempotencyService` for resilience controls.
6. The matching test class for each concern to see intended behavior and edge cases.
