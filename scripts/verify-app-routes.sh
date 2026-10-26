#!/usr/bin/env bash
set -euo pipefail

# Manual-verification script for the AppRoutes extraction: regenerates a
# scratch service, starts it for real (Postgres + sbt run), and curls the
# routes now assembled by AppRoutes.scala — Swagger UI, the raw AsyncAPI spec,
# the rendered static AsyncAPI HTML, and a business endpoint — confirming the
# extraction and the fileService wiring work end to end.
# Mirrors this repo's own dev-regenerate.sh workflow (docs/developing-the-template.md).
#
# Requires: sbt, Docker, Node.js/npm (for the AsyncAPI HTML generation),
# GITHUB_TOKEN/GITHUB_ACTOR set (purerestlib resolution).
#
# Usage:
#   ./scripts/verify-app-routes.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN_NAME="approutescheck"
DEV_DIR="$REPO_DIR/.dev/${DOMAIN_NAME}-service"
BASE_URL="http://localhost:8080/${DOMAIN_NAME}s"

cleanup() {
  echo "== Cleaning up =="
  [ -n "${SBT_PID:-}" ] && kill "$SBT_PID" 2>/dev/null || true
  (cd "$DEV_DIR" && docker compose down 2>/dev/null) || true
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

echo "== Regenerating a scratch instance =="
"$SCRIPT_DIR/dev-regenerate.sh" --domain-name "$DOMAIN_NAME"

[ -f "$DEV_DIR/target/asyncapi-docs/index.html" ] || \
  fail "missing $DEV_DIR/target/asyncapi-docs/index.html - Node.js/npm unavailable?"

echo "== Starting Postgres =="
(cd "$DEV_DIR" && docker compose up -d)

echo "== Starting the service =="
(cd "$DEV_DIR" && sbt run) &
SBT_PID=$!

echo "== Waiting for /health =="
for _ in $(seq 1 60); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/health 2>/dev/null | grep -q 200; then
    break
  fi
  sleep 2
done

echo "== GET /docs (expect 200, Swagger UI) =="
STATUS="$(curl -sL -o /dev/null -w '%{http_code}' http://localhost:8080/docs)"
[ "$STATUS" = "200" ] || fail "GET /docs returned $STATUS"

echo "== GET /docs/asyncapi.json (expect the registered event type) =="
SPEC_BODY="$(curl -sS http://localhost:8080/docs/asyncapi.json)"
echo "$SPEC_BODY" | grep -q "ExampleEvent" || \
  fail "GET /docs/asyncapi.json does not contain ExampleEvent"

echo "== GET /docs/asyncapi/index.html (expect the rendered static HTML) =="
STATUS="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/docs/asyncapi/index.html)"
[ "$STATUS" = "200" ] || fail "GET /docs/asyncapi/index.html returned $STATUS"
HTML_BODY="$(curl -sS http://localhost:8080/docs/asyncapi/index.html)"
echo "$HTML_BODY" | grep -q "ExampleEvent" || \
  fail "rendered AsyncAPI HTML does not contain ExampleEvent"

echo "== POST $BASE_URL + GET the created entity (expect 201 then 200) =="
CREATE_BODY="$(curl -sS -X POST -H 'Content-Type: application/json' -d '{}' -w '\n%{http_code}' "$BASE_URL")"
CREATE_STATUS="$(echo "$CREATE_BODY" | tail -n1)"
[ "$CREATE_STATUS" = "201" ] || fail "POST $BASE_URL returned $CREATE_STATUS"
ENTITY_ID="$(echo "$CREATE_BODY" | head -n1 | python3 -c 'import sys, json; print(json.load(sys.stdin)["id"])')"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/$ENTITY_ID")"
[ "$STATUS" = "200" ] || fail "GET $BASE_URL/$ENTITY_ID returned $STATUS"

echo "== All AppRoutes checks passed =="
