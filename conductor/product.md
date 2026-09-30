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
Once the template could reliably reproduce an equivalent instance on demand, the standalone
reference implementation was retired (Iteration 6) — the generator's own output is now the
product, not a parallel hand-maintained copy.

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
The generator produces a microservice exposing full CRUD on a single domain resource
(parameterized by `domain_name`) backed by its own dedicated Postgres database (one database
per service, named after the domain), plus standard production-quality `GET /health`/`GET
/health/ready` endpoints — fully observable via purerest (tracing, structured logging, metrics),
fully tested (unit coverage per endpoint, an integration test proving the full CRUD lifecycle
against a real database via Testcontainers). Resilience (retry, circuit breaker) is demonstrated
as a tested, PureConfig-driven example rather than live-wired by default, since a freshly
generated service makes no outbound calls of its own — see the repo's README for the pattern a
generated service would actually use. Originally proven out as a single hand-adapted reference
implementation (`order-service`, from purerest's own reference service of the same name) before
the generator machinery was built on top of it; that standalone copy was retired once the
generator could reproduce an equivalent instance on demand (Iteration 6).

## Components
1. **The generator** — a giter8 template (`src/main/g8/`, Iteration 2) that parameterizes a
   service's domain/package via two properties (`domain_name`, `package`).
2. **The field-codegen tool** (`tools/codegen/`, Iteration 3) — a standalone post-generation
   step that adds every domain field to a generated service (whose base entity is just
   `id`/`createdAt`/`updatedAt`), driven by a small YAML field-spec with per-field
   create/update visibility (Iteration 5).
3. **The publish pipeline** (`scripts/generate-and-publish-service.sh`, Iteration 4) — chains
   generation (+ optional field-codegen) into a single command that creates a public GitHub repo,
   pushes the generated service, and waits for its GitHub Actions CI run to go green.

## Key Features (built)
1. A giter8 template (`src/main/g8/`) that generates a new, differently-domained service — two
   properties, `domain_name` (drives naming/paths/DB) and `package` (independently settable,
   supports reverse-domain nesting). Verified end-to-end: a generated service compiles, passes
   its full test suite, and every README-documented endpoint works against a live instance.
2. A field-codegen tool (`tools/codegen/`) that extends a generated service with extra domain
   fields from a YAML field-spec (name/type/example/visibility/default;
   String/Int/Boolean/Instant), rewriting the
   case class, DTOs, SQL, store (in-memory + Postgres), and tests via stable anchor comments
   embedded in the giter8 template. Verified end-to-end: generates a service, applies a
   4-field/all-types spec, and the result passes its full (field-extended) test suite.
3. A publish pipeline (`scripts/generate-and-publish-service.sh`) that generates a service,
   optionally applies a field-spec, reformats it, creates a public GitHub repo, pushes, wires
   up a `GH_PACKAGES_TOKEN` CI secret (the automatic built-in `GITHUB_TOKEN` can't resolve
   another repo's GitHub Packages), and waits for the triggered GitHub Actions run to pass.
   Verified against three real runs: a plain generate+publish, one with `--field-spec`, and a
   repo-name collision aborting cleanly with no side effects.
4. A field-spec-driven base entity: the g8 template ships with zero hardcoded domain fields
   (`id`/`createdAt`/`updatedAt` only) and an optional per-field `visibility`
   (`create-and-update`/`create-only`/`server-defaulted`, plus `default` for the latter) lets
   field-codegen express asymmetric create/update patterns. Verified by dogfooding: expressing
   the original order-service's `item`/`quantity`/`status` fields as a field-spec reproduces its
   generated code and behavior exactly (52/52 tests).
5. A template-development workflow (`scripts/dev-regenerate.sh`/`scripts/dev-diff.sh`) —
   generates a disposable, compiler-checked scratch instance (plus a frozen `.baseline` sibling)
   to develop the template against, and a plain diff between them to review before manually
   porting a change back into `src/main/g8/`. Deliberately no auto-patching of the template
   (see `docs/developing-the-template.md` for why).

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

## Iteration 3 Goals (2026-09-29) — completed 2026-09-29
Add parametrized field codegen on top of the proven giter8 template, so a generated service isn't
limited to the fixed item/quantity/status example fields.

1. **A field-spec-driven post-generation tool.** ✅ Answered — `tools/codegen/`, a standalone sbt
   project (not aggregated into the reference service's build), parses a YAML field-spec and
   rewrites every insertion point in a generated project via anchor comments embedded in the g8
   template. Verified against a real generated service: 52/52 tests pass after applying a
   4-field spec covering every supported type.

## Iteration 4 Goals (2026-09-29) — completed 2026-09-29
Automate the last manual step: turning a generated service into a real, pushed, CI-verified
repo — this project's answer to `service-generator`'s Jenkins pipeline.

1. **A single-command generate → publish → CI pipeline.** ✅ Answered —
   `scripts/generate-and-publish-service.sh`. Verified live against real GitHub repos (created,
   pushed, CI green, then deleted) — including a bug live-testing surfaced and fixed: the
   script wasn't running the documented `sbt scalafmt` reformat pass before committing, which
   made the very first CI run fail its own scalafmtCheck.

## Iteration 5 Goals (2026-09-29) — completed 2026-09-29
Generalize field-codegen so the template's base entity carries no domain-specific fields at
all — the last piece of the original `service-generator`-parity requirements list.

1. **Per-field create/update visibility.** ✅ Answered — an optional `visibility` key
   (`create-and-update`/`create-only`/`server-defaulted`) plus `default` for server-defaulted
   fields, backward compatible with every existing field-spec. Verified by dogfooding
   item/quantity/status themselves through the real pipeline with full parity, and by two
   live runs (`verify-g8-template.sh`, `generate-and-publish-service.sh`) with no regressions.

## Iteration 6 Goals (2026-09-30) — completed 2026-09-30
Retire the standalone order-service reference implementation now that the generator can
reproduce an equivalent instance on demand, and reframe the project's docs around the generator
(not a specific service) as the deliverable.

1. **Remove the reference service.** ✅ Answered — order-service had diverged from what it was
   meant to demonstrate (still hardcoded item/quantity/status, while the *template's* base
   entity was generalized to field-spec-driven id/createdAt/updatedAt in Iteration 5) and
   duplicated what `generate-and-publish-service.sh` can now produce on demand. Removed
   `src/main/scala/orderservice/`, its tests, and the root sbt project
   (`build.sbt`/`project/`/`docker-compose.yml`/`.scalafmt.conf`) — `tools/codegen/` and the
   template's own build are independent and unaffected.
2. **A template-development workflow to replace it.** ✅ Answered —
   `scripts/dev-regenerate.sh`/`scripts/dev-diff.sh`, documented in
   `docs/developing-the-template.md`.
3. **Split the docs.** ✅ Answered — `README.md` (generator-first overview),
   `tools/codegen/README.md` (field-spec format), `docs/developing-the-template.md` (the dev
   workflow).

## Non-Goals (for now)
- Optional/nullable fields in the field-spec (v1 requires all fields).
- Private-repo support for the publish pipeline (public only for now).
- Kubernetes manifests.
- Correct pluralization for irregular English domain names in generated REST paths — the
  template does naive `+s` only; documented as a known limitation in the README.

## Future Direction (under consideration)
A `--private` flag for the publish pipeline.
