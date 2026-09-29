# Spec: Create pure-service-generator project from order-service reference implementation

## Overview
First track of the pure-service-generator initiative. Creates this standalone repository
containing a production-quality reference microservice adapted from purerest's `order-service`,
enforcing a one-database-per-service pattern with full CRUD on the domain resource. This is the
foundation later tracks build on: a g8-based rename template, parametrized field-codegen, and
CI/CD generation are explicitly deferred to follow-on backlog tracks.

## Functional Requirements
1. Standalone sbt project — own `build.sbt`, no dependency on purerest's source tree.
2. purerest consumed only as a **published GitHub Packages dependency** (`io.github.beckfordp`
   `purerestlib`, version-pinned) — same real-external-consumer proof purerest's `smoke-test/`
   already established.
3. **One database per service, named after the domain**: a dedicated Postgres database named
   `order` (singular — the domain name), with an `order` table representing the domain resource
   — diverges from purerest's own pluralized `orders`/`orders` naming, adopted here as the
   convention this reference template demonstrates. (purerest's own `order-service` is
   untouched by this track.)
4. **Full CRUD on the REST interface, all modifying the real database by default:**
   - `POST /orders` — create (existing)
   - `GET /orders/{id}` — read (existing)
   - `PATCH /orders/{id}` — partial update (new — quantity/status; adds an `updated_at` column,
     set on every update)
   - `DELETE /orders/{id}` — delete (new — 204, subsequent GET 404s)
   `OrderStore[F]` gains `update`/`delete` methods, implemented for both the in-memory
   (unit-test) and Postgres (integration) backends.
5. Carry over order-service's tracing, structured logging, metrics, tapir routes/OpenAPI/Swagger
   docs, PureConfig-driven `application.conf`, Flyway migrations, Docker image via
   sbt-native-packager. (Resilience — retry + circuit breaker — is **not** carried over; see
   "Deviations from order-service" below.)
6. Add missing standard production-quality endpoints:
   - `GET /health` — liveness, always 200.
   - `GET /health/ready` — readiness, verifies real Postgres connectivity, 200/503.
7. Full unit test coverage for every endpoint: create, read, patch, delete, health, ready.
8. Integration tests (Testcontainers Postgres) proving the full CRUD lifecycle against a real
   database — create → read → patch → delete → read-404 — plus the readiness endpoint against a
   real container.
9. Pure-FP discipline maintained throughout: tagless-final algebras, F-polymorphic, `Ref` not
   `var`, narrowest-typeclass discipline.

## Acceptance Criteria
- Repo builds/tests standalone (`sbt test`), zero dependency on purerest's source tree.
- Postgres database/table named `order` (singular); documented as the one-db-per-service
  convention in this repo's README.
- All four CRUD endpoints implemented, tapir/Docs.routes-wired (visible in Swagger),
  unit-tested.
- `GET /health` / `GET /health/ready` implemented and unit-tested.
- A Testcontainers integration test proves the full CRUD lifecycle end-to-end against real
  Postgres.

## Deviations from order-service (recorded 2026-09-29)
While planning Phase 1's port, reading order-service's actual code (not just its build.sbt
comments) surfaced a coupling the original spec didn't account for: `POST /orders` calls a real
`inventory-service` over HTTP at runtime (`InventoryClient.reserve`, wired in `Main.scala`) to
reserve stock before persisting the order, and the `orders` table stores the resulting
`reservation_id`/`reserved_quantity`. This is a genuine runtime dependency, not just the
test-scope `.dependsOn(inventoryService % Test)` purerest's `tech-stack.md` describes — and it
directly conflicts with this track's "own `docker-compose.yml` (Postgres only)" requirement:
with no inventory-service running, every `POST /orders` would 503. It's also the *only* place
order-service's resilience (retry + circuit breaker) wiring applies — that middleware wraps an
http4s `Client[F]`, and the inventory call is the only outbound HTTP call in the service.

**Decision (user-confirmed):**
- Drop `InventoryClient`/`ReservationView` and the `reservation_id`/`reserved_quantity` columns
  entirely. `POST /orders` creates the order directly against Postgres — no outbound HTTP call,
  fully self-sufficient with just the `order` database.
- Drop the resilience (retry + circuit breaker) requirement from this track's scope — there is no
  other outbound call to wrap it around, and adding one purely to exercise unused middleware
  would be scope creep beyond what this track needs.

  **Amendment (2026-09-29):** since this repo's purpose is to be copied/adapted into services
  that *will* call other services, resilience is kept as a documented, tested example rather than
  dropped outright — not wired into `Main`/the live service, but a standalone test
  (`ClientResilienceExampleSuite`) wrapping a dummy `Client[F]` with `Resilience.middleware`,
  demonstrating the retry + circuit-breaker pattern for whoever adapts this template. See
  README's "Calling other services with resilience".
- Test suites that only exist to exercise the inventory-service integration
  (`OrderServiceIntegrationSuite`, `OrderServicePostgresIntegrationSuite`,
  `OrderServiceTraceContinuitySuite` — all import `inventoryservice.*` directly, which isn't
  available as this repo has no dependency on inventory-service's source) are not ported.
  `OrderRoutesSuite`, `OrderStoreSuite`, `OrderStorePostgresSuite`, `OrderServiceConfigSuite`,
  `OrderDocsSuite`, and `MigrationsSuite` have no such dependency and are ported.

See `tech-stack.md`'s "Known Constraints" for the corresponding tech-stack note.

## Out of Scope (separate follow-on backlog items)
- g8-based rename templating (service/domain/package name)
- Parametrized field-codegen
- Automated generation → repo-creation → push → CI pipeline
- Kubernetes manifests
- Renaming purerest's own order-service's DB/table in the purerest repo
