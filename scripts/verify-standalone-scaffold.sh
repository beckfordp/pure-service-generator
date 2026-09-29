#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 1: confirms this repo
# is a genuine standalone consumer of purerest, not a copy that happens to compile:
#   - build.sbt has no .dependsOn back to purerest's source tree, and resolves
#     purerestlib from GitHub Packages.
#   - docker-compose.yml only starts Postgres (no inventory-service, per this track's
#     recorded deviation dropping the inventory-service runtime coupling).
#   - sbt scalafmtCheck test passes against the published jar.
#
# Requires GITHUB_ACTOR/GITHUB_TOKEN (a PAT with read:packages scope) in the
# environment, to resolve purerestlib from GitHub Packages.
#
# Usage: ./scripts/verify-standalone-scaffold.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0

cleanup() {
  echo
  echo "Cleaning up..."
  docker compose down >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [ -z "${GITHUB_ACTOR:-}" ] || [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "FAIL: GITHUB_ACTOR/GITHUB_TOKEN must be set to resolve purerestlib from GitHub Packages." >&2
  exit 1
fi

echo "1. Checking build.sbt has no .dependsOn (a real standalone consumer, not a source link)..."
if grep -q '\.dependsOn(' build.sbt; then
  echo "   FAIL: build.sbt still contains .dependsOn" >&2
  FAILED=1
else
  echo "   OK: no .dependsOn in build.sbt"
fi

echo
echo "2. Checking build.sbt resolves purerestlib from GitHub Packages..."
if grep -q 'maven.pkg.github.com/beckfordp/purerest' build.sbt && grep -q '"io.github.beckfordp" %% "purerestlib"' build.sbt; then
  echo "   OK: purerestlib resolved from GitHub Packages"
else
  echo "   FAIL: expected a GitHub Packages resolver + purerestlib dependency in build.sbt" >&2
  FAILED=1
fi

echo
echo "3. Checking docker-compose.yml only starts Postgres (no inventory-service)..."
services="$(docker compose config --services)"
if [ "$services" = "postgres" ]; then
  echo "   OK: docker-compose.yml defines only 'postgres'"
else
  echo "   FAIL: expected only 'postgres', got: $services" >&2
  FAILED=1
fi

echo
echo "4. docker compose up -d (Postgres)..."
docker compose up -d
for _ in $(seq 1 60); do
  status="$(docker compose ps --format '{{.Health}}' postgres 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 1
done
if [ "$status" != "healthy" ]; then
  echo "   FAIL: Postgres did not become healthy" >&2
  FAILED=1
else
  echo "   OK: Postgres is healthy"
fi

echo
echo "5. sbt scalafmtCheck test (against the published purerestlib jar)..."
if sbt -batch scalafmtCheck test; then
  echo "   OK: scalafmtCheck + full test suite passed"
else
  echo "   FAIL: sbt scalafmtCheck test failed" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
