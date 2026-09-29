#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 7: confirms
# PUT /orders/{id} works end-to-end against the real service and a real
# Postgres database, not just in unit/Testcontainers tests.
#
# Usage: ./scripts/verify-put-endpoint.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT=8080
FAILED=0
LOG_FILE="$(mktemp -t verify-put-endpoint)"

cleanup() {
  echo
  echo "Cleaning up..."
  [ -n "${SBT_PID:-}" ] && { kill "$SBT_PID" >/dev/null 2>&1 || true; wait "$SBT_PID" 2>/dev/null || true; }
  pids="$(lsof -ti "tcp:${PORT}" 2>/dev/null || true)"
  [ -n "$pids" ] && echo "$pids" | xargs kill >/dev/null 2>&1 || true
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "1. docker compose up -d (fresh Postgres)..."
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
echo "2. Starting order-service (plain foreground 'sbt run', backgrounded at the shell level)..."
sbt run >"$LOG_FILE" 2>&1 </dev/null &
SBT_PID=$!
for _ in $(seq 1 90); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/nope" || true)"
  [ "$code" != "000" ] && break
  sleep 1
done
if [ "$code" = "000" ]; then
  echo "   FAIL: order-service did not become ready." >&2
  cat "$LOG_FILE"
  exit 1
fi
echo "   OK: order-service is up"

echo
echo "3. POST /orders to create an order..."
create_response="$(curl -s -X POST "http://localhost:${PORT}/orders" \
  -H 'Content-Type: application/json' -d '{"item":"widget","quantity":2}')"
order_id="$(echo "$create_response" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
if [ -z "$order_id" ]; then
  echo "   FAIL: POST /orders did not return an id. Response: $create_response" >&2
  exit 1
fi
echo "   OK: created order ${order_id}"

echo
echo "4. PUT /orders/${order_id} with a full replacement body..."
put_status="$(curl -s -o /tmp/verify-put-body.json -w '%{http_code}' -X PUT \
  "http://localhost:${PORT}/orders/${order_id}" \
  -H 'Content-Type: application/json' -d '{"quantity":9,"status":"shipped"}')"
put_body="$(cat /tmp/verify-put-body.json)"
if [ "$put_status" = "200" ] && echo "$put_body" | grep -q '"quantity":9' && echo "$put_body" | grep -q '"status":"shipped"'; then
  echo "   OK: PUT returned 200 with quantity=9, status=shipped"
else
  echo "   FAIL: expected 200 with quantity=9/status=shipped, got ${put_status}: $put_body" >&2
  FAILED=1
fi

echo
echo "5. GET /orders/${order_id} to confirm the replacement persisted..."
get_body="$(curl -s "http://localhost:${PORT}/orders/${order_id}")"
if echo "$get_body" | grep -q '"quantity":9' && echo "$get_body" | grep -q '"status":"shipped"'; then
  echo "   OK: GET reflects the persisted replacement"
else
  echo "   FAIL: expected persisted quantity=9/status=shipped, got: $get_body" >&2
  FAILED=1
fi

echo
echo "6. PUT /orders/{unknown-id} returns 404..."
unknown_status="$(curl -s -o /dev/null -w '%{http_code}' -X PUT \
  "http://localhost:${PORT}/orders/00000000-0000-0000-0000-000000000000" \
  -H 'Content-Type: application/json' -d '{"quantity":1,"status":"x"}')"
if [ "$unknown_status" = "404" ]; then
  echo "   OK: unknown id returns 404"
else
  echo "   FAIL: expected 404, got ${unknown_status}" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  tail -40 "$LOG_FILE" >&2
  exit 1
fi
