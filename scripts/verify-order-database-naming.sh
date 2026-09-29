#!/usr/bin/env bash
set -euo pipefail

# Manual verification for pure-service-generator_20260929 Phase 2: confirms the
# one-database-per-service rename actually took effect at runtime, not just in the
# migration file:
#   - docker-compose.yml provisions a Postgres database named "order" (singular).
#   - the real migration file applies cleanly against it via psql, creating a quoted
#     "order" table with the expected columns, including updated_at.
#
# (sbt test's OrderStorePostgresSuite/MigrationsSuite already prove Migrations.run
# and OrderStore's Skunk queries work against this exact schema, via Testcontainers -
# this script's job is only to confirm docker-compose's own Postgres is wired up
# with the same "order" naming, independent of that.)
#
# Usage: ./scripts/verify-order-database-naming.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0

cleanup() {
  echo
  echo "Cleaning up..."
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "1. Checking docker-compose.yml provisions a database named 'order'..."
db_name="$(docker compose config | awk '/POSTGRES_DB:/ { print $2; exit }')"
if [ "$db_name" = "order" ]; then
  echo "   OK: POSTGRES_DB=order"
else
  echo "   FAIL: expected POSTGRES_DB=order, got: ${db_name:-<empty>}" >&2
  FAILED=1
fi

echo
echo "2. docker compose up -d (Postgres)..."
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
echo "3. Applying the real migration file against the 'order' database via psql..."
if docker compose exec -T postgres psql -U order -d order \
  < src/main/resources/db/migration/V1__create_order_table.sql; then
  echo "   OK: migration applied"
else
  echo "   FAIL: migration failed to apply" >&2
  exit 1
fi

echo
echo "4. Checking the 'order' table exists with the expected columns, including updated_at..."
columns="$(docker compose exec -T postgres psql -U order -d order -tAc \
  "select column_name from information_schema.columns where table_name = 'order' order by ordinal_position" \
  | tr -d '\r' | paste -sd, -)"
expected="id,item,quantity,status,created_at,updated_at"
if [ "$columns" = "$expected" ]; then
  echo "   OK: order table columns match: $columns"
else
  echo "   FAIL: expected columns '$expected', got '$columns'" >&2
  FAILED=1
fi

echo
echo "5. Inserting and reading back a row (proves the quoted \"order\" identifier works end-to-end)..."
docker compose exec -T postgres psql -U order -d order -c \
  "insert into \"order\" (id, item, quantity) values (gen_random_uuid(), 'widget', 2)" >/dev/null
row_count="$(docker compose exec -T postgres psql -U order -d order -tAc \
  "select count(*) from \"order\" where item = 'widget'" | tr -d '\r')"
if [ "$row_count" = "1" ]; then
  echo "   OK: insert + select against \"order\" round-tripped"
else
  echo "   FAIL: expected 1 row, got '$row_count'" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
