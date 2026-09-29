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
/health/ready` endpoints — fully observable and resilient via purerest, fully tested (unit
coverage per endpoint, an integration test proving the full CRUD lifecycle against a real
database via Testcontainers).

## Components
1. **The reference service** (this repo's initial focus) — a standalone sbt project, not part
   of purerest's own build, consuming purerest as a published dependency.
2. **The generator** (future) — name/domain/package templating (likely via giter8/g8),
   parametrized field codegen, and generation-time CI/CD wiring, built on top of the reference
   service once it's solid.

## Key Features (built)
*(none yet — Iteration 1 is in progress)*

## Iteration 1 Goals (2026-09-29)
Get a genuinely production-quality reference microservice working end-to-end before building
any generic generator machinery on top of it.

1. **Stand up the reference microservice as its own standalone project.** Ported from
   purerest's `order-service`, consuming `purerestlib` only as a published GitHub Packages
   dependency.
2. **Enforce one database per service, named after the domain.** A dedicated Postgres database
   and primary table both named `order` (singular), not the pluralized `orders` naming
   purerest's own reference `order-service` currently uses.
3. **Full CRUD on the domain resource, by default persisted.** `POST`/`GET`/`PATCH`/`DELETE
   /orders` (id-addressed), each backed by real Postgres reads/writes.
4. **Standard production-quality endpoints.** `GET /health` (liveness) and `GET /health/ready`
   (readiness, verifying real database connectivity).
5. **Full test coverage.** A unit test per endpoint, plus a Testcontainers-backed integration
   test proving the complete CRUD lifecycle against a real database.

## Non-Goals (for now)
- g8-based (or any) rename templating (service/domain/package name) — tracked as a future
  iteration once the reference service above is solid.
- Parametrized field codegen (an entity-field list expanding into a case class field + DB
  migration + codec wiring) — deferred; needs its own design once the reference service's shape
  is proven.
- Automated generation → repo-creation → push → CI pipeline (this project's eventual answer to
  `service-generator`'s Jenkins pipeline).
- Kubernetes manifests.

## Future Direction (under consideration)
Once Iteration 1's reference service is solid: a g8-based template for service/domain/package
renaming, parametrized field codegen, and a generation-time pipeline that creates a new repo,
pushes the generated service, and runs its tests — informed by `service-generator`'s existing
Jenkins-based version of the same idea, reimplemented without Java/Spring/Jenkins/Groovy.
