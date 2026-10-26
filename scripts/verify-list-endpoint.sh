#!/usr/bin/env bash
set -euo pipefail

# Manual-verification script for the list-endpoint track
# (conductor/tracks/list-endpoint_20261026/): regenerates a scratch service,
# starts it for real (Postgres + sbt run), seeds a few entities, and curls the
# generated list endpoint to confirm pagination, X-Total-Count, the 400
# validation path, and CORS exposure actually work on the wire — not just
# asserted against in-memory routes in the generated test suite.
# Mirrors this repo's own dev-regenerate.sh workflow (docs/developing-the-template.md).
#
# Requires: sbt, Docker, python3, GITHUB_TOKEN/GITHUB_ACTOR set (purerestlib resolution).
#
# Usage:
#   ./scripts/verify-list-endpoint.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN_NAME="listendpointcheck"
DEV_DIR="$REPO_DIR/.dev/${DOMAIN_NAME}-service"
BASE_URL="http://localhost:8080/${DOMAIN_NAME}s"
TMP_DIR="$(mktemp -d)"

LAST_BODY=""
LAST_HEADERS=""

cleanup() {
  echo "== Cleaning up =="
  [ -n "${SBT_PID:-}" ] && kill "$SBT_PID" 2>/dev/null || true
  (cd "$DEV_DIR" && docker compose down 2>/dev/null) || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# GET $1 -> sets LAST_BODY / LAST_HEADERS to temp files.
fetch() {
  LAST_BODY="$TMP_DIR/body.$RANDOM"
  LAST_HEADERS="$TMP_DIR/headers.$RANDOM"
  curl -sS -D "$LAST_HEADERS" -o "$LAST_BODY" "$1"
}

header_is() { # headers-file, header-name, expected-value
  grep -qi "^$2: $3\\(\r\\)\\?$" "$1" || fail "expected '$2: $3' in headers, got: $(cat "$1")"
}

echo "== Regenerating a scratch instance =="
"$SCRIPT_DIR/dev-regenerate.sh" --domain-name "$DOMAIN_NAME"

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

echo "== Seeding three entities (oldest -> newest) =="
IDS=()
for _ in 1 2 3; do
  BODY="$(curl -sS -X POST -H 'Content-Type: application/json' -d '{}' "$BASE_URL")"
  IDS+=("$(python3 -c 'import sys, json; print(json.load(sys.stdin)["id"])' <<<"$BODY")")
  sleep 0.05
done
echo "seeded: ${IDS[*]}"

echo "== GET default page (expect newest-first, 3 rows, X-Total-Count: 3) =="
fetch "$BASE_URL"
header_is "$LAST_HEADERS" "X-Total-Count" "3"
python3 - "$LAST_BODY" "${IDS[2]}" "${IDS[1]}" "${IDS[0]}" <<'PY' || fail "default page is not newest-first"
import json, sys
rows = json.load(open(sys.argv[1]))
ids = [row["id"] for row in rows]
expected = sys.argv[2:]
if ids != expected:
    sys.exit(f"expected {expected}, got {ids}")
PY

echo "== GET ?limit=1&offset=1 (expect the middle entity) =="
fetch "$BASE_URL?limit=1&offset=1"
header_is "$LAST_HEADERS" "X-Total-Count" "3"
python3 - "$LAST_BODY" "${IDS[1]}" <<'PY' || fail "limit/offset did not return the expected page"
import json, sys
rows = json.load(open(sys.argv[1]))
ids = [row["id"] for row in rows]
if ids != [sys.argv[2]]:
    sys.exit(f"expected [{sys.argv[2]}], got {ids}")
PY

echo "== GET ?offset=10 (expect [] and the full total via the count fallback) =="
fetch "$BASE_URL?offset=10"
header_is "$LAST_HEADERS" "X-Total-Count" "3"
python3 - "$LAST_BODY" <<'PY' || fail "offset past the end did not return an empty page"
import json, sys
rows = json.load(open(sys.argv[1]))
if rows != []:
    sys.exit(f"expected [], got {rows}")
PY

echo "== Invalid pagination (expect 400 for limit=0, limit=101, offset=-1) =="
for query in "limit=0" "limit=101" "offset=-1"; do
  STATUS="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL?$query")"
  [ "$STATUS" = "400" ] || fail "expected 400 for ?$query, got $STATUS"
done

echo "== CORS (expect X-Total-Count exposed to browser JS) =="
CORS_HEADERS="$(curl -sD - -o /dev/null -H "Origin: http://example.com" "$BASE_URL")"
echo "$CORS_HEADERS" | grep -i "Access-Control-Allow-Origin" >/dev/null || \
  fail "no Access-Control-Allow-Origin on list response"
echo "$CORS_HEADERS" | grep -i "Access-Control-Expose-Headers" | grep -qi "X-Total-Count" || \
  fail "X-Total-Count is not exposed via Access-Control-Expose-Headers"

echo "== All list-endpoint checks passed =="
