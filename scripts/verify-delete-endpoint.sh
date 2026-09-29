#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 4: confirms
# DELETE /orders/{id} works end-to-end against the real service and a real
# Postgres database, not just in unit/Testcontainers tests.
#
# Usage: ./scripts/verify-delete-endpoint.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT=8080
FAILED=0
LOG_FILE="$(mktemp -t verify-delete-endpoint)"

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
echo "4. DELETE /orders/${order_id} returns 204..."
delete_status="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "http://localhost:${PORT}/orders/${order_id}")"
if [ "$delete_status" = "204" ]; then
  echo "   OK: DELETE returned 204"
else
  echo "   FAIL: expected 204, got ${delete_status}" >&2
  FAILED=1
fi

echo
echo "5. GET /orders/${order_id} now returns 404..."
get_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/orders/${order_id}")"
if [ "$get_status" = "404" ]; then
  echo "   OK: order is gone (404)"
else
  echo "   FAIL: expected 404, got ${get_status}" >&2
  FAILED=1
fi

echo
echo "6. DELETE /orders/{unknown-id} returns 404..."
unknown_status="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  "http://localhost:${PORT}/orders/00000000-0000-0000-0000-000000000000")"
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
