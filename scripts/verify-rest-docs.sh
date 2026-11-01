#!/usr/bin/env bash
set -euo pipefail

# Manual-verification script for the rest-docs track
# (conductor/tracks/rest-docs_20261101/, gluon TD-20.2): regenerates a scratch
# service with a sample field-spec, starts it for real (Postgres + sbt run),
# and checks the served OpenAPI spec at /docs/docs.yaml carries the endpoint
# summaries/descriptions/tags, parameter and error descriptions, and the
# codegen'd fields' descriptions and examples - not just that the generated
# suite's Docs.undocumented check passes in-process.
# Mirrors this repo's own dev-regenerate.sh workflow (docs/developing-the-template.md).
#
# Requires: sbt, Docker, GITHUB_TOKEN/GITHUB_ACTOR set (purerestlib resolution).
#
# Usage:
#   ./scripts/verify-rest-docs.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN_NAME="restdocscheck"
DEV_DIR="$REPO_DIR/.dev/${DOMAIN_NAME}-service"
TMP_DIR="$(mktemp -d)"
SBT_LOG="$TMP_DIR/sbt-run.log"
FIELD_SPEC="$TMP_DIR/field-spec.yaml"
SPEC="$TMP_DIR/docs.yaml"

cleanup() {
  echo "== Cleaning up =="
  if [ -n "${SBT_PID:-}" ]; then
    pkill -9 -P "$SBT_PID" 2>/dev/null || true
    kill -9 "$SBT_PID" 2>/dev/null || true
    wait "$SBT_PID" 2>/dev/null || true
  fi
  (cd "$DEV_DIR" && docker compose down 2>/dev/null) || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# The YAML renderer folds long scalars across lines, so match against the spec
# with every whitespace run collapsed to one space.
spec_has() { # expected substring of the served spec
  tr -s '[:space:]' ' ' <"$SPEC" | grep -qF -- "$1" || fail "expected '$1' in /docs/docs.yaml"
}

cat >"$FIELD_SPEC" <<'YAML'
fields:
  - name: label
    type: String
    example: "front door"
    description: "Human-readable label."
    visibility: create-only
  - name: count
    type: Int
    example: "3"
    description: "How many there are."
  - name: checkedAt
    type: Instant
    example: "2026-11-01T10:00:00Z"
    description: "When it was last checked (UTC)."
  - name: state
    type: String
    example: "active"
    description: "Where it is in its lifecycle."
    visibility: server-defaulted
    default: "new"
YAML

echo "== Regenerating a scratch instance (with a field-spec) =="
"$SCRIPT_DIR/dev-regenerate.sh" --domain-name "$DOMAIN_NAME" --field-spec "$FIELD_SPEC"

echo "== Starting Postgres =="
(cd "$DEV_DIR" && docker compose up -d)

echo "== Starting the service =="
(cd "$DEV_DIR" && exec sbt -batch run >"$SBT_LOG" 2>&1 </dev/null) &
SBT_PID=$!

echo "== Waiting for /health =="
HEALTHY=0
for _ in $(seq 1 60); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/health 2>/dev/null | grep -q 200; then
    HEALTHY=1
    break
  fi
  sleep 2
done
[ "$HEALTHY" -eq 1 ] || {
  tail -50 "$SBT_LOG" >&2 || true
  fail "service did not become healthy"
}

echo "== Fetching /docs/docs.yaml =="
curl -sSf -o "$SPEC" http://localhost:8080/docs/docs.yaml

echo "== Endpoint summaries, descriptions and tags =="
for summary in "Create a ${DOMAIN_NAME}" "List ${DOMAIN_NAME}s" "Get a ${DOMAIN_NAME}" \
  "Update a ${DOMAIN_NAME}" "Replace a ${DOMAIN_NAME}" "Delete a ${DOMAIN_NAME}" \
  "Liveness check" "Readiness check"; do
  spec_has "summary: $summary"
done
spec_has "Not idempotent: each call creates a new ${DOMAIN_NAME}."
spec_has "- ${DOMAIN_NAME}s"
spec_has "- health"

echo "== Parameters and headers =="
spec_has "id (UUID). A malformed id returns 400."
spec_has "3f2b8c1e-7a4d-4e9b-9c55-0d6e2a1b7f40"
spec_has "Page size, 1–100. Defaults to 20."
spec_has "Total ${DOMAIN_NAME}s across all pages."

echo "== Errors =="
spec_has "No ${DOMAIN_NAME} with this id."
spec_has "Invalid pagination parameters"
spec_has "The store didn't answer a ping."

echo "== DTOs, including codegen'd fields =="
spec_has "A ${DOMAIN_NAME} as stored."
spec_has "When the ${DOMAIN_NAME} was created (UTC)."
spec_has "Human-readable label."
spec_has "front door"
spec_has "When it was last checked (UTC)."
spec_has "2026-11-01T10:00:00Z"

echo "PASS: /docs/docs.yaml documents every endpoint, parameter, DTO field and error"
