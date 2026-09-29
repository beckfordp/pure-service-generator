#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 6's final task:
# builds the real Docker image (sbt-native-packager) and runs it against a real
# docker-compose Postgres - not sbt run - confirming every endpoint responds
# correctly end-to-end from the packaged artifact a real deployment would use.
#
# Usage: ./scripts/verify-docker-image.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT=8080
FAILED=0
CONTAINER_NAME="verify-order-service"

cleanup() {
  echo
  echo "Cleaning up..."
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "1. Building the Docker image (sbt Docker/publishLocal)..."
sbt -batch Docker/publishLocal >/tmp/verify-docker-build.log 2>&1
if docker image inspect order-service:latest >/dev/null 2>&1; then
  echo "   OK: order-service:latest built"
else
  echo "   FAIL: order-service:latest image not found after build" >&2
  tail -40 /tmp/verify-docker-build.log >&2
  exit 1
fi

echo
echo "2. docker compose up -d (fresh Postgres)..."
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d
for _ in $(seq 1 60); do
  status="$(docker compose ps --format '{{.Health}}' postgres 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 1
done
if [ "$status" != "healthy" ]; then
  echo "   FAIL: Postgres did not become healthy" >&2
  exit 1
fi
echo "   OK: Postgres is healthy"

echo
echo "3. Running the built image on the compose network, pointed at Postgres..."
network="$(docker compose ps -q postgres | xargs docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}')"
docker run -d --rm --name "$CONTAINER_NAME" \
  --network "$network" \
  -e POSTGRES_HOST=postgres \
  -p "${PORT}:8080" \
  order-service:latest >/dev/null
for _ in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health" || true)"
  [ "$code" != "000" ] && break
  sleep 1
done
if [ "$code" = "000" ]; then
  echo "   FAIL: containerized order-service did not become ready." >&2
  docker logs "$CONTAINER_NAME" >&2 || true
  exit 1
fi
echo "   OK: containerized order-service is up"

echo
echo "4. Exercising the full CRUD + health surface against the container..."

create_response="$(curl -s -X POST "http://localhost:${PORT}/orders" \
  -H 'Content-Type: application/json' -d '{"item":"widget","quantity":3}')"
order_id="$(echo "$create_response" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
if [ -n "$order_id" ]; then
  echo "   OK: POST /orders created ${order_id}"
else
  echo "   FAIL: POST /orders did not return an id. Response: $create_response" >&2
  FAILED=1
fi

get_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/orders/${order_id}")"
[ "$get_status" = "200" ] && echo "   OK: GET /orders/{id} returned 200" || {
  echo "   FAIL: GET /orders/{id} expected 200, got ${get_status}" >&2
  FAILED=1
}

patch_status="$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "http://localhost:${PORT}/orders/${order_id}" \
  -H 'Content-Type: application/json' -d '{"quantity":9,"status":"shipped"}')"
[ "$patch_status" = "200" ] && echo "   OK: PATCH /orders/{id} returned 200" || {
  echo "   FAIL: PATCH /orders/{id} expected 200, got ${patch_status}" >&2
  FAILED=1
}

delete_status="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "http://localhost:${PORT}/orders/${order_id}")"
[ "$delete_status" = "204" ] && echo "   OK: DELETE /orders/{id} returned 204" || {
  echo "   FAIL: DELETE /orders/{id} expected 204, got ${delete_status}" >&2
  FAILED=1
}

get_after_delete_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/orders/${order_id}")"
[ "$get_after_delete_status" = "404" ] && echo "   OK: GET after DELETE returned 404" || {
  echo "   FAIL: GET after DELETE expected 404, got ${get_after_delete_status}" >&2
  FAILED=1
}

health_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health")"
[ "$health_status" = "200" ] && echo "   OK: GET /health returned 200" || {
  echo "   FAIL: GET /health expected 200, got ${health_status}" >&2
  FAILED=1
}

ready_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health/ready")"
[ "$ready_status" = "200" ] && echo "   OK: GET /health/ready returned 200" || {
  echo "   FAIL: GET /health/ready expected 200, got ${ready_status}" >&2
  FAILED=1
}

docs_status="$(curl -sL -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/docs")"
[ "$docs_status" = "200" ] && echo "   OK: GET /docs (Swagger UI) returned 200" || {
  echo "   FAIL: GET /docs expected 200, got ${docs_status}" >&2
  FAILED=1
}

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  docker logs "$CONTAINER_NAME" >&2 || true
  exit 1
fi
