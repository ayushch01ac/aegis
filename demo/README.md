# Aegis end-to-end demo

This directory supplies a deliberately small **separate** Orders service and a Docker Compose stack. It demonstrates Aegis in its intended position: a client calls Aegis, which applies its controls and forwards to a downstream HTTP application.

The Orders service is not part of the Aegis production artifact and has no dependency on Aegis. It only returns the forwarded `X-Request-Id`, which makes request propagation visible in the demo.

## Run it

From the repository root:

```bash
docker compose -f demo/compose.yaml up --build -d
curl --retry 15 --retry-connrefused http://localhost:8080/actuator/health
```

The Compose defaults are demo-only values. Override `AEGIS_DEMO_DATABASE_PASSWORD`, `AEGIS_DEMO_JWT_SECRET`, and `AEGIS_DEMO_ADMIN_PASSWORD` in your shell before starting the stack if needed. Do not use them outside this local demonstration.

## Configure the route and send traffic

First request a token. Copy its `accessToken` value into `TOKEN`.

```bash
curl -sS http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  --data '{"username":"demo-admin","password":"demo-password-change-before-sharing"}'
```

Create the downstream route. `orders-service` is the Docker service name, so Aegis can resolve it on the internal Compose network.

```bash
export TOKEN='paste-access-token-here'

curl -i http://localhost:8080/api/v1/routes \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{
    "name":"orders-service",
    "baseUrl":"http://orders-service:8081",
    "timeoutMs":2000,
    "priority":"HIGH",
    "enabled":true,
    "rateLimitAlgorithm":"TOKEN_BUCKET",
    "rateLimitCapacity":5,
    "rateLimitRefillRate":1
  }'
```

Now make the request through Aegis rather than directly to port 8081:

```bash
curl -i http://localhost:8080/api/v1/proxy/orders-service/orders/demo-42 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Request-Id: portfolio-demo-42'
```

The response contains the Orders service payload, including `requestId: "portfolio-demo-42"`, plus Aegis's `X-RateLimit-*` headers. That makes the gateway and downstream boundary visible in a short screen recording or live interview walkthrough.

To demonstrate idempotency, run the same command twice with the same `Idempotency-Key`; the second response is replayed by Aegis rather than creating another order.

```bash
curl -i http://localhost:8080/api/v1/proxy/orders-service/orders \
  -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: portfolio-order-1' \
  --data '{"sku":"coffee-beans","quantity":2}'
```

Stop and remove the demo resources with:

```bash
docker compose -f demo/compose.yaml down --volumes
```
