#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 5: confirms
# GET /health and GET /health/ready work end-to-end against the real service,
# including readiness correctly flipping to 503 when Postgres is unreachable.
#
# Usage: ./scripts/verify-health-endpoints.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT=8080
FAILED=0
LOG_FILE="$(mktemp -t verify-health-endpoints)"

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
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health" || true)"
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
echo "3. GET /health returns 200..."
health_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health")"
if [ "$health_status" = "200" ]; then
  echo "   OK: /health returned 200"
else
  echo "   FAIL: expected 200, got ${health_status}" >&2
  FAILED=1
fi

echo
echo "4. GET /health/ready returns 200 while Postgres is up..."
ready_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health/ready")"
if [ "$ready_status" = "200" ]; then
  echo "   OK: /health/ready returned 200"
else
  echo "   FAIL: expected 200, got ${ready_status}" >&2
  FAILED=1
fi

echo
echo "5. Stopping Postgres, then GET /health/ready returns 503..."
docker compose stop postgres >/dev/null
for _ in $(seq 1 30); do
  ready_status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health/ready")"
  [ "$ready_status" = "503" ] && break
  sleep 1
done
if [ "$ready_status" = "503" ]; then
  echo "   OK: /health/ready returned 503 once Postgres was down"
else
  echo "   FAIL: expected 503, got ${ready_status}" >&2
  FAILED=1
fi

echo
echo "6. GET /health still returns 200 even though Postgres is down (liveness != readiness)..."
health_status_down="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/health")"
if [ "$health_status_down" = "200" ]; then
  echo "   OK: /health still returns 200"
else
  echo "   FAIL: expected 200, got ${health_status_down}" >&2
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
