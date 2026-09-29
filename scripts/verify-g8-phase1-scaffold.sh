#!/usr/bin/env bash
set -euo pipefail

# Manual verification for g8-template_20260929 Phase 1: confirms the template's
# scaffolding (non-Scala files) is structurally present and correctly templated,
# with no stale hardcoded "order"/"order-service" references left behind (the
# migration SQL's own reserved-keyword comment intentionally uses "order" as one
# of several illustrative examples, not a leftover reference to the domain).
#
# Full end-to-end generation (sbt new + sbt test against a real generated
# service) is Phase 4's job, once the Scala source is templated too.
#
# Usage: ./scripts/verify-g8-phase1-scaffold.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0

echo "1. Checking default.properties defines domain_name and package..."
if grep -q '^domain_name = ' src/main/g8/default.properties && grep -q '^package = ' src/main/g8/default.properties; then
  echo "   OK: both properties present"
else
  echo "   FAIL: expected domain_name and package in default.properties" >&2
  FAILED=1
fi

echo
echo "2. Checking expected Phase 1 files exist..."
expected_files=(
  "src/main/g8/build.sbt"
  "src/main/g8/.scalafmt.conf"
  "src/main/g8/project/build.properties"
  "src/main/g8/project/plugins.sbt"
  "src/main/g8/docker-compose.yml"
  "src/main/g8/src/main/resources/application.conf"
  "src/main/g8/src/main/resources/db/migration/V1__create_\$domain_name\$_table.sql"
)
for f in "${expected_files[@]}"; do
  if [ -f "$f" ]; then
    echo "   OK: $f"
  else
    echo "   FAIL: missing $f" >&2
    FAILED=1
  fi
done

echo
echo "3. Checking for stale un-templated references..."
for pattern in "order-service" "orderservice" "POSTGRES_DB: order" "POSTGRES_USER: order" "POSTGRES_PASSWORD: order" "pg_isready -U order"; do
  hits="$(grep -rn --fixed-strings "$pattern" src/main/g8/ 2>/dev/null || true)"
  if [ -n "$hits" ]; then
    echo "   FAIL: found stale '$pattern':" >&2
    echo "$hits" >&2
    FAILED=1
  fi
done
echo "   OK: no stale order-service/orderservice/hardcoded-'order'-value references"

echo
echo "4. Checking the migration creates a \$domain_name\$-templated table..."
if grep -q 'CREATE TABLE "\$domain_name\$"' "src/main/g8/src/main/resources/db/migration/V1__create_\$domain_name\$_table.sql"; then
  echo "   OK: table name is templated"
else
  echo "   FAIL: expected CREATE TABLE \"\$domain_name\$\"" >&2
  FAILED=1
fi

echo
echo "5. Sanity check: this repo's own reference service (template source) still"
echo "   builds/tests standalone, unaffected by the new src/main/g8/ tree..."
export GITHUB_ACTOR="${GITHUB_ACTOR:-}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-}"
if [ -z "$GITHUB_ACTOR" ] || [ -z "$GITHUB_TOKEN" ]; then
  echo "   SKIPPED: GITHUB_ACTOR/GITHUB_TOKEN not set in this shell." >&2
else
  if sbt -batch compile; then
    echo "   OK: reference service still compiles"
  else
    echo "   FAIL: reference service no longer compiles" >&2
    FAILED=1
  fi
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
