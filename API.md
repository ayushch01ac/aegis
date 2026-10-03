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
  "enabled": true
}
```

Rules:

- `name` matches `[A-Za-z0-9._-]+` and has at most 100 characters.
- `baseUrl` is an absolute `http` or `https` URL with a host and has at most 500 characters.
- `timeoutMs` is between 1 and 120000.
- `priority` is `CRITICAL`, `HIGH`, `NORMAL`, or `LOW`.
- Omitted `enabled` defaults to `true`.

Create returns `201 Created` and `Location: /api/v1/routes/{id}`. Delete returns `204 No Content`.

List requests accept `page` (zero based) and `size`; the maximum size is 100. The response contains `content`, `page`, `size`, `totalElements`, and `totalPages`.

### Proxy

Forwards a live request to the downstream service registered under the given route name. The route must exist and be enabled. The downstream HTTP status, headers, and body are returned verbatim; hop-by-hop headers are stripped.

```http
ANY /api/v1/proxy/{routeName}/**
```

Any HTTP method is accepted. The path after `{routeName}` is appended to the route's `baseUrl`. Query strings are forwarded. The `X-Request-Id` correlation header is forwarded to the downstream service.

Examples:

```http
GET  /api/v1/proxy/orders-service/orders/42
POST /api/v1/proxy/payments-service/payments?idempotencyKey=abc
```

Rules:

- `ADMIN`, `OPERATOR`, and `VIEWER` may send proxy requests.
- The route must be enabled; disabled routes return `503 ROUTE_DISABLED`.
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
| 503 | `ROUTE_DISABLED` | The proxy target route is currently disabled |
| 502 | `DOWNSTREAM_ERROR` | The downstream call timed out or failed |
| 500 | `INTERNAL_ERROR` | An unexpected failure occurred |

Example:

```json
{
  "timestamp": "2026-10-03T10:00:00Z",
  "status": 502,
  "code": "DOWNSTREAM_ERROR",
  "message": "Downstream call failed for route: orders-service",
  "requestId": "req-123",
  "fieldErrors": null
}
```

OpenAPI is available at `/v3/api-docs`; Swagger UI is available at `/swagger-ui.html`.

## Planned API boundaries

The following are design directions, not available endpoints: per-route rate-limit policy configuration and idempotency-key support for selected side-effecting operations. Their exact paths and schemas will be documented only when the corresponding phases are implemented.
