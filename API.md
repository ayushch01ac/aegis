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

`/api/v1/status` is the simple application status endpoint. `/actuator/health` is the Spring Boot health endpoint.

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

### Errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | A request field failed validation |
| 400 | `MALFORMED_REQUEST` | JSON body is missing or unreadable |
| 400 | `INVALID_PARAMETER` | A path or query parameter has an invalid type |
| 404 | `ROUTE_NOT_FOUND` | The route ID does not exist |
| 409 | `ROUTE_NAME_CONFLICT` | Another route already uses the name |
| 401 | `INVALID_CREDENTIALS` | Username/password authentication failed |
| 401 | `UNAUTHORIZED` | A protected endpoint has no valid JWT |
| 403 | `ACCESS_DENIED` | JWT role cannot perform the action |
| 500 | `INTERNAL_ERROR` | An unexpected failure occurred |

Example:

```json
{
  "timestamp": "2026-10-02T12:00:00Z",
  "status": 409,
  "code": "ROUTE_NAME_CONFLICT",
  "message": "Route name already exists: orders-service",
  "requestId": "req-123",
  "fieldErrors": null
}
```

OpenAPI is available at `/v3/api-docs`; Swagger UI is available at `/swagger-ui.html`.

## Planned API boundaries

The following are design directions, not available endpoints: a proxy path for configured routes, per-route rate-limit policy configuration, and idempotency-key support for selected side-effecting operations. Their exact paths and schemas will be documented only when the corresponding phases are implemented.
