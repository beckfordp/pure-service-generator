# Product Guide

## Vision
`pure-service-generator` grows into a generator for new microservices built on
[purerest](https://github.com/beckfordp/purerest) — a pure-functional-programming platform
library (Scala 3, Cats Effect, http4s) supplying observability (tracing, structured logging,
metrics) and resilience (retry, circuit breaker) infrastructure as composable building blocks.
Rather than starting from a generator engine and working backwards, this project starts from a
single, genuinely production-quality reference microservice — everything a real service needs
(full CRUD on its domain resource, one dedicated database per service, health/readiness
endpoints, full test coverage, a deployable image) — and only *then* builds the generic
generator machinery (name/package templating, field codegen, CI/CD wiring) on top of a template
that has actually been proven out end-to-end.

## Target Users
- Internal engineering (primarily the author) — a personal platform-engineering project and
  learning vehicle, and the intended eventual starting point for any future purerest-based
  microservice.

## Relationship to purerest
This project is purerest's first genuine external consumer: it resolves `purerestlib` as a
published GitHub Packages dependency (never vendored, never a source/`.dependsOn` link), the
same proof-of-real-consumption purerest's own `smoke-test/` module established. Requirements
for *what a generated service should look like* are informed by an existing Java/Spring-based
prior-art project, `service-generator` (parameterized name/package/entity-field generation,
Docker/CI-per-generated-service) — the *requirements shape* is worth borrowing; the
Spring/Java/Jenkins/Groovy *implementation* is not. This project's own implementation follows
purerest's own design guidelines instead (see `product-guidelines.md`): pure functional
programming, no side effects outside the effect type, tagless-final style throughout.

## Core Use Case
A reference microservice, adapted from purerest's own `order-service`, exposing full CRUD on a
single domain resource (`order`) backed by its own dedicated Postgres database (one database
per service, named after the domain), plus standard production-quality `GET /health`/`GET
/health/ready` endpoints — fully observable via purerest (tracing, structured logging, metrics),
fully tested (unit coverage per endpoint, an integration test proving the full CRUD lifecycle
against a real database via Testcontainers). Resilience (retry, circuit breaker) is demonstrated
as a tested, PureConfig-driven example rather than live-wired, since this reference service makes
no outbound calls of its own — see the repo's README for the pattern a generated service would
actually use.

## Components
1. **The reference service** — a standalone sbt project, not part of purerest's own build,
   consuming purerest as a published dependency.
2. **The generator** — a giter8 template (`src/main/g8/`, Iteration 2) that renames the reference
   service's domain/package via two properties (`domain_name`, `package`). Parametrized field
   codegen and generation-time CI/CD wiring remain future work, built on top of this once needed.

## Key Features (built)
1. Standalone sbt project (own `build.sbt`, no source link back to purerest) consuming
   `purerestlib` as a published GitHub Packages dependency — this project's own real-external-
   consumer proof.
2. One database per service: a dedicated Postgres database and primary table, both named `order`
   (singular) — quoted throughout since `order` is a reserved PostgreSQL keyword.
3. Full CRUD on `/orders`: `POST` (create), `GET` (read), `PATCH` (partial update), `PUT` (full
   replace, added mid-iteration by request), `DELETE` — all persisted to real Postgres.
4. `GET /health` (liveness, always 200) and `GET /health/ready` (readiness — a real `SELECT 1`
   against Postgres, 200/503).
5. Full test coverage: a unit test per endpoint (in-memory `OrderStore`), Postgres-backed tests
   per `OrderStore` method via Testcontainers, and one integration test proving the complete
   create → read → update → delete lifecycle plus a readiness check against a real container.
6. A deployable Docker image (`sbt-native-packager`), verified end-to-end — built, run against
   real Postgres, and exercised through the full CRUD + health surface.
7. A tested, PureConfig-driven example (`ClientResilienceExampleSuite`) demonstrating purerest's
   retry/circuit-breaker pattern for whoever adapts this template to call a real downstream
   service — this reference service itself makes no outbound calls, so the middleware isn't
   live-wired (see Non-Goals-adjacent deviation note below).
8. A giter8 template (`src/main/g8/`) that generates a new, differently-domained service from
   the reference service — two properties, `domain_name` (drives naming/paths/DB) and `package`
   (independently settable, supports reverse-domain nesting). Verified end-to-end: a generated
   service compiles, passes its full test suite (identical to the source repo's own), and every
   README-documented endpoint works against a live instance.

## Iteration 1 Goals (2026-09-29) — completed 2026-09-29
Get a genuinely production-quality reference microservice working end-to-end before building
any generic generator machinery on top of it.

1. **Stand up the reference microservice as its own standalone project.** ✅ Answered — ported
   from purerest's `order-service`, consuming `purerestlib` only as a published GitHub Packages
   dependency (never `.dependsOn`).
2. **Enforce one database per service, named after the domain.** ✅ Answered — a dedicated
   Postgres database and primary table both named `order` (singular), not the pluralized
   `orders` naming purerest's own reference `order-service` uses.
3. **Full CRUD on the domain resource, by default persisted.** ✅ Answered, and expanded mid-
   iteration by request from four endpoints to five: `POST`/`GET`/`PATCH`/`PUT`/`DELETE /orders`
   (id-addressed), each backed by real Postgres reads/writes. While porting order-service, found
   its `POST /orders` genuinely depended on a live `inventory-service` at runtime (not just a
   test-scope dependency, as purerest's own docs implied) — incompatible with this repo's
   standalone, Postgres-only footprint, so that coupling (and the resilience middleware that
   existed solely to wrap it) was dropped from the live service, kept only as a tested example.
   See `conductor/tech-stack.md` and the track's `spec.md` for the full deviation record.
4. **Standard production-quality endpoints.** ✅ Answered — `GET /health` (liveness) and
   `GET /health/ready` (readiness, verifying real database connectivity).
5. **Full test coverage.** ✅ Answered — a unit test per endpoint, Postgres-level tests per
   `OrderStore` method, plus a Testcontainers-backed integration test proving the complete CRUD
   lifecycle against a real database.

## Iteration 2 Goals (2026-09-29) — completed 2026-09-29
With the reference service solid (Iteration 1), turn it into a giter8 template so a new
purerest-based service can be generated by renaming rather than hand-editing.

1. **A giter8 template renaming service/domain/package.** ✅ Answered — `src/main/g8/`
   templates the entire reference service, parameterized by `domain_name` and an independently-
   settable `package` (defaulting to `$domain_name$service`, but overridable to e.g. a
   reverse-domain style). Verified by actually generating a "widget" service and running its
   full test suite (52/52 pass, identical to the source repo's own).
   Two environment/tooling gotchas surfaced along the way, now documented in
   `conductor/tech-stack.md`: giter8's capitalize-format name is lowercase `cap` (not `Cap`, which
   is silently ignored rather than erroring), and every literal `$` in template content (Scala
   string interpolation, Skunk's `$`-based SQL parameters) must be escaped as `\$`. Also: this
   sbt version's built-in `new` command doesn't resolve arbitrary `file://` URIs (only a hardcoded
   list of GitHub shortcuts) — generation instead uses giter8's own `giter8-launcher` library
   directly, documented in the README.

## Non-Goals (for now)
- Parametrized field codegen (an entity-field list expanding into a case class field + DB
  migration + codec wiring) — deferred; needs its own design now that the template's naming
  parameterization is proven.
- Automated generation → repo-creation → push → CI pipeline (this project's eventual answer to
  `service-generator`'s Jenkins pipeline).
- Kubernetes manifests.
- Correct pluralization for irregular English domain names in generated REST paths — the
  template does naive `+s` only; documented as a known limitation in the README.

## Future Direction (under consideration)
Parametrized field codegen, and a generation-time pipeline that creates a new repo, pushes the
generated service, and runs its tests — informed by `service-generator`'s existing Jenkins-based
version of the same idea, reimplemented without Java/Spring/Jenkins/Groovy.
