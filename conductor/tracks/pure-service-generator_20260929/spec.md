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
5. Carry over order-service's full wiring as-is: tracing, structured logging, metrics,
   resilience (retry + circuit breaker), tapir routes/OpenAPI/Swagger docs, PureConfig-driven
   `application.conf`, Flyway migrations, Docker image via sbt-native-packager.
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

## Out of Scope (separate follow-on backlog items)
- g8-based rename templating (service/domain/package name)
- Parametrized field-codegen
- Automated generation → repo-creation → push → CI pipeline
- Kubernetes manifests
- Renaming purerest's own order-service's DB/table in the purerest repo
