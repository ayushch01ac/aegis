# API

Base URL: `http://localhost:8080`  
Current version: `v1`  
Authentication: Bearer JWT required for route APIs

All JSON error responses include a `requestId`. Aegis echoes a supplied `X-Request-Id` or generates one when it is absent.

## Authentication

On first startup, Aegis creates one `ADMIN` account from `AEGIS_BOOTSTRAP_ADMIN_USERNAME` and `AEGIS_BOOTSTRAP_ADMIN_PASSWORD`. The password is stored as a BCrypt hash; changing these environment variables later does not replace the stored account password.

```http
POST /api/v1/auth/token
```

```json
{
  "username": "admin",
  "password": "your-password"
}
```

Successful responses contain an HMAC-signed access token, its `Bearer` type, and its expiry timestamp. Send it as:

```http
Authorization: Bearer <accessToken>
```

`ADMIN` can create, update, and delete routes. `ADMIN`, `OPERATOR`, and `VIEWER` can read routes. `/actuator/health`, `/api/v1/status`, OpenAPI, Swagger UI, and the token endpoint are public.

## Implemented endpoints

### Status

```http
GET /api/v1/status
GET /actuator/health
```

`/api/v1/status` is the simple application status endpoint. `/actuator/health` is the Spring Boot health endpoint, which monitors connectivity to both PostgreSQL (`db`) and Redis (`redis`).

### Routes

These are administration APIs for durable downstream-service configuration; they do not forward client traffic.

```http
POST   /api/v1/routes
GET    /api/v1/routes
GET    /api/v1/routes/{id}
PUT    /api/v1/routes/{id}
DELETE /api/v1/routes/{id}
```

Create or update body:

```json
{
  "name": "orders-service",
  "baseUrl": "http://orders:8080",
  "timeoutMs": 2000,
  "priority": "HIGH",
  "enabled": true,
  "rateLimitAlgorithm": "TOKEN_BUCKET",
  "rateLimitCapacity": 100,
  "rateLimitWindowSeconds": 60,
  "rateLimitRefillRate": 10
}
```

Rules:

- `name` matches `[A-Za-z0-9._-]+` and has at most 100 characters.
- `baseUrl` is an absolute `http` or `https` URL with a host and has at most 500 characters.
- `timeoutMs` is between 1 and 120000.
- `priority` is `CRITICAL`, `HIGH`, `NORMAL`, or `LOW`.
- Omitted `enabled` defaults to `true`.
- `rateLimitAlgorithm` is optional (`FIXED_WINDOW` or `TOKEN_BUCKET`).
- `rateLimitCapacity` is optional (1–1,000,000). Max permits/tokens.
- `rateLimitWindowSeconds` is optional (1–86,400). Window size for fixed-window.
- `rateLimitRefillRate` is optional (1–1,000,000). Refill rate in tokens/sec for token-bucket.

Create returns `201 Created` and `Location: /api/v1/routes/{id}`. Delete returns `204 No Content`.

List requests accept `page` (zero based) and `size`; the maximum size is 100. The response contains `content`, `page`, `size`, `totalElements`, and `totalPages`.

### Proxy

Forwards a live request to the downstream service registered under the given route name. The route must exist and be enabled. The downstream HTTP status, headers, and body are returned verbatim; hop-by-hop headers are stripped.

```http
ANY /api/v1/proxy/{routeName}/**
```

Any HTTP method is accepted. The path after `{routeName}` is appended to the route's `baseUrl`. Query strings are forwarded. The `X-Request-Id` correlation header is forwarded to the downstream service.

Response headers added by Aegis:
- `X-RateLimit-Limit`: Maximum allowed limit or bucket capacity
- `X-RateLimit-Remaining`: Remaining permitted requests or tokens

Examples:

```http
GET  /api/v1/proxy/orders-service/orders/42
POST /api/v1/proxy/payments-service/payments?idempotencyKey=abc
```

Rules:

- `ADMIN`, `OPERATOR`, and `VIEWER` may send proxy requests.
- The route must be enabled; disabled routes return `503 ROUTE_DISABLED`.
- Exceeding the route's rate limit returns `429 RATE_LIMIT_EXCEEDED` with a `Retry-After` header.
- Network failures, connect timeouts, and read timeouts return `502 DOWNSTREAM_ERROR`.
- Per-route `timeoutMs` is used as the read-side deadline. Connect timeout is controlled by `AEGIS_PROXY_CONNECT_TIMEOUT_MS` (default 3 000 ms).

### Errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | A request field failed validation |
| 400 | `MALFORMED_REQUEST` | JSON body is missing or unreadable |
| 400 | `INVALID_PARAMETER` | A path or query parameter has an invalid type |
| 404 | `ROUTE_NOT_FOUND` | The route ID or name does not exist |
| 409 | `ROUTE_NAME_CONFLICT` | Another route already uses the name |
| 401 | `INVALID_CREDENTIALS` | Username/password authentication failed |
| 401 | `UNAUTHORIZED` | A protected endpoint has no valid JWT |
| 403 | `ACCESS_DENIED` | JWT role cannot perform the action |
| 429 | `RATE_LIMIT_EXCEEDED` | Request rate limit for the target route was exceeded |
| 503 | `ROUTE_DISABLED` | The proxy target route is currently disabled |
| 502 | `DOWNSTREAM_ERROR` | The downstream call timed out or failed |
| 500 | `INTERNAL_ERROR` | An unexpected failure occurred |

Example rate limit error response:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
Retry-After: 5
X-Request-Id: req-123
```

```json
{
  "timestamp": "2026-10-03T10:00:00Z",
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded for route: orders-service. Try again in 5 seconds.",
  "requestId": "req-123",
  "fieldErrors": null
}
```

OpenAPI is available at `/v3/api-docs`; Swagger UI is available at `/swagger-ui.html`.

## Planned API boundaries

The following are design directions, not available endpoints: idempotency-key support for selected side-effecting operations. Their exact paths and schemas will be documented only when the corresponding phases are implemented.
