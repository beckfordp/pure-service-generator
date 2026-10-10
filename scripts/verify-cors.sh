#!/usr/bin/env bash
set -euo pipefail

# Manual-verification script for the CORS track (conductor/tracks/cors_20261010/):
# regenerates a scratch service, starts it for real (Postgres + sbt run), and
# curls it with a real Origin header to confirm CORS headers are actually
# present on the wire — not just asserted against a dummy route in CorsSuite.
# Mirrors this repo's own dev-regenerate.sh workflow (docs/developing-the-template.md).
#
# Requires: sbt, Docker, GITHUB_TOKEN/GITHUB_ACTOR set (purerestlib resolution).
#
# Usage:
#   ./scripts/verify-cors.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN_NAME="corscheck"
DEV_DIR="$REPO_DIR/.dev/${DOMAIN_NAME}-service"

cleanup() {
  echo "== Cleaning up =="
  [ -n "${SBT_PID:-}" ] && kill "$SBT_PID" 2>/dev/null || true
  (cd "$DEV_DIR" && docker compose down 2>/dev/null) || true
}
trap cleanup EXIT

echo "== Regenerating a scratch instance =="
"$SCRIPT_DIR/dev-regenerate.sh" --domain-name "$DOMAIN_NAME"

echo "== Starting Postgres =="
(cd "$DEV_DIR" && docker compose up -d)

echo "== Starting the service =="
(cd "$DEV_DIR" && sbt run) &
SBT_PID=$!

echo "== Waiting for /health =="
for _ in $(seq 1 30); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/health 2>/dev/null | grep -q 200; then
    break
  fi
  sleep 2
done

echo "== GET with Origin header (expect Access-Control-Allow-Origin, even on a non-matching route) =="
GET_HEADERS="$(curl -sD - -o /dev/null -H "Origin: http://example.com" "http://localhost:8080/${DOMAIN_NAME}s")"
echo "$GET_HEADERS" | grep -i "Access-Control-Allow-Origin" || {
  echo "FAIL: no Access-Control-Allow-Origin on GET response" >&2
  exit 1
}

echo "== OPTIONS preflight for POST (expect allow-methods/allow-headers) =="
OPTIONS_HEADERS="$(curl -sD - -o /dev/null -X OPTIONS \
  -H "Origin: http://example.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  "http://localhost:8080/${DOMAIN_NAME}s")"
echo "$OPTIONS_HEADERS" | grep -i "Access-Control-Allow-Methods" | grep -q "POST" || {
  echo "FAIL: Access-Control-Allow-Methods missing or doesn't include POST" >&2
  exit 1
}
echo "$OPTIONS_HEADERS" | grep -qi "Access-Control-Allow-Headers.*Content-Type" || {
  echo "FAIL: Access-Control-Allow-Headers missing or doesn't include Content-Type" >&2
  exit 1
}

echo "== No Access-Control-Allow-Credentials should be sent =="
if echo "$GET_HEADERS" | grep -qi "Access-Control-Allow-Credentials"; then
  echo "FAIL: Access-Control-Allow-Credentials should not be present" >&2
  exit 1
fi

echo "PASS: CORS headers present and correct on a real running instance."
