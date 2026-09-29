#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 3: confirms
# PATCH /orders/{id} works end-to-end against the real service and a real
# Postgres database, not just in unit/Testcontainers tests.
#
# Usage: ./scripts/verify-patch-endpoint.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT=8080
FAILED=0
LOG_FILE="$(mktemp -t verify-patch-endpoint)"

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
echo "2. Starting order-service (plain foreground 'sbt run', backgrounded at the shell level -"
echo "   see tech-stack.md's 'sbt bgRun hangs when scripted' note for why not bgRun)..."
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
echo "4. PATCH /orders/${order_id} with a new quantity and status..."
patch_status="$(curl -s -o /tmp/verify-patch-body.json -w '%{http_code}' -X PATCH \
  "http://localhost:${PORT}/orders/${order_id}" \
  -H 'Content-Type: application/json' -d '{"quantity":9,"status":"shipped"}')"
patch_body="$(cat /tmp/verify-patch-body.json)"
if [ "$patch_status" = "200" ] && echo "$patch_body" | grep -q '"quantity":9' && echo "$patch_body" | grep -q '"status":"shipped"'; then
  echo "   OK: PATCH returned 200 with quantity=9, status=shipped"
else
  echo "   FAIL: expected 200 with quantity=9/status=shipped, got ${patch_status}: $patch_body" >&2
  FAILED=1
fi

echo
echo "5. GET /orders/${order_id} to confirm the change persisted..."
get_body="$(curl -s "http://localhost:${PORT}/orders/${order_id}")"
if echo "$get_body" | grep -q '"quantity":9' && echo "$get_body" | grep -q '"status":"shipped"'; then
  echo "   OK: GET reflects the persisted update"
else
  echo "   FAIL: expected persisted quantity=9/status=shipped, got: $get_body" >&2
  FAILED=1
fi

echo
echo "6. PATCH /orders/{unknown-id} returns 404..."
unknown_status="$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
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
