# Plan: Create pure-service-generator project from order-service reference implementation

## Phase 1: Scaffold the standalone repository [checkpoint: 1ab7933]
- [x] Task: Port order-service's source tree from purerest into this repo as a standalone sbt
      project: own `build.sbt` resolving `purerestlib` 0.1.0 from GitHub Packages (not
      `.dependsOn`), own `docker-compose.yml` (Postgres only). Per this track's "Deviations from
      order-service" (spec.md, 2026-09-29): strip `InventoryClient`/`ReservationView` and the
      `reservation_id`/`reserved_quantity` columns/fields during the port itself (no outbound
      HTTP call, no resilience middleware — order-service's runtime dependency on a live
      inventory-service is incompatible with this repo's Postgres-only footprint). Port and adapt
      `OrderRoutesSuite`, `OrderStoreSuite`, `OrderStorePostgresSuite`, `OrderServiceConfigSuite`,
      `OrderDocsSuite`, `MigrationsSuite` (dropping inventory/reservation assertions to match);
      confirm they pass against the published jar — this phase's "green" proof. [5e3b1c0]
- [x] Task: Conductor - User Manual Verification 'Phase 1: Scaffold the standalone repository'
      (Protocol in workflow.md). Prompting off: verified via `scripts/verify-standalone-scaffold.sh`
      instead of an interactive walkthrough. [1ab7933]

## Phase 2: Adopt one-database-per-service naming [checkpoint: b4f3ad6]
- [x] Task: Flatten the ported V1/V2 migrations into one initial migration creating a singular
      `order` table (domain name, not `orders`); rename the Postgres database to `order` in
      `docker-compose.yml`/`application.conf` defaults; add an `updated_at TIMESTAMPTZ` column
      for the update endpoint in Phase 3. [06463a5]
- [x] Task: Verify the ported `OrderStorePostgresSuite` passes against the renamed
      database/table. [06463a5]
- [x] Task: Conductor - User Manual Verification 'Phase 2: Adopt one-database-per-service
      naming' (Protocol in workflow.md). Prompting off: verified via
      `scripts/verify-order-database-naming.sh` instead of an interactive walkthrough. [b4f3ad6]

## Phase 3: Add `PATCH /orders/{id}` (update) [checkpoint: 04b5b11]
- [x] Task: Write failing tests (Red) — extend `OrderStoreSuite` with an `update` test
      (in-memory store); confirm it fails to compile (no `update` method yet). [ae00e61]
- [x] Task: Implement (Green) — add `update(id, quantity, status): F[Option[Order]]` to
      `OrderStore[F]`, both in-memory and Postgres backends (sets `updated_at`); add a tapir
      `PATCH /orders/{id}` endpoint + `OrderRoutes` wiring; extend `OrderRoutesSuite` for the
      new endpoint. Run the suite, confirm green. [354559e]
- [x] Task: Conductor - User Manual Verification 'Phase 3: Add PATCH /orders/{id}' (Protocol in
      workflow.md). Prompting off: verified via `scripts/verify-patch-endpoint.sh` instead of an
      interactive walkthrough. [04b5b11]

## Phase 4: Add `DELETE /orders/{id}` [checkpoint: 3c0e2e2]
- [x] Task: Write failing tests (Red) — extend `OrderStoreSuite` with a `delete` test; confirm
      it fails to compile. [17b99a4]
- [x] Task: Implement (Green) — add `delete(id): F[Boolean]` to `OrderStore[F]`, both backends;
      add a tapir `DELETE /orders/{id}` endpoint (204, subsequent `GET` 404s) + routing; extend
      `OrderRoutesSuite`. Run the suite, confirm green. [a76958e]
- [x] Task: Conductor - User Manual Verification 'Phase 4: Add DELETE /orders/{id}' (Protocol in
      workflow.md). Prompting off: verified via `scripts/verify-delete-endpoint.sh` instead of
      an interactive walkthrough. [3c0e2e2]

## Phase 5: Add `GET /health` and `GET /health/ready`
- [x] Task: Write failing tests (Red) — new `HealthRoutesSuite`; confirm it fails to compile (no
      `HealthRoutes` yet). [2b6469f]
- [x] Task: Implement (Green) — `HealthRoutes` with a liveness endpoint (always 200) and a
      readiness endpoint backed by a new `OrderStore.ping: F[Boolean]` (a trivial `SELECT 1`
      against Postgres, always `true` for the in-memory backend), wired into `Docs.routes`
      alongside the order endpoints. Run the suite, confirm green. [4bc21fd]
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Add GET /health and GET /health/ready'
      (Protocol in workflow.md)

## Phase 6: Full CRUD lifecycle integration test, README, and final polish
- [x] Task: Add `ClientResilienceExampleSuite` (`src/test/scala/orderservice/examples/`) —
      wraps a dummy `Client[F]` with purerest's `Resilience.middleware`, demonstrating the
      retry + circuit-breaker pattern for whoever adapts this template to call a real downstream
      service, since it's dropped from the live service per the Phase 1 deviation (spec.md
      amendment, 2026-09-29). README points to it ("Calling other services with resilience").
      Pulled forward from this phase and done now, per explicit user request. [d3ef76d]
- [ ] Task: Write failing test (Red) — extend `OrderStorePostgresSuite` (Testcontainers) with a
      full lifecycle test: create → read → patch → delete → read-404, plus a readiness-check
      test against the real container. Confirm it fails for the right reason if run against a
      pre-Phase-3/4 checkout.
- [ ] Task: Implement (Green) — fix anything the lifecycle test surfaces; run full suite,
      confirm green.
- [ ] Task: Write this repo's `README.md` (prerequisites, quickstart, one-db-per-service note,
      Swagger UI link) mirroring order-service's own docs.
- [ ] Task: Build the Docker image and run the full stack once via `docker compose up`,
      confirming all endpoints respond correctly end-to-end.
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Full CRUD lifecycle integration test,
      README, and final polish' (Protocol in workflow.md)
