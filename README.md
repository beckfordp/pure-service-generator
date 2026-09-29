# pure-service-generator

A production-quality reference microservice — `order-service` — built on
[purerest](https://github.com/beckfordp/purerest), adapted as a standalone repository: its own
`build.sbt`, resolving `purerestlib` as a published GitHub Packages dependency rather than a
source link. Eventually this becomes a generator for new purerest-based services; today it's one
hand-adapted reference implementation. See
[`conductor/product.md`](./conductor/product.md) for the vision and
[`conductor/tracks.md`](./conductor/tracks.md) for in-progress/planned work.

## Prerequisites

- sbt / JDK 21 (for building and running)
- Docker Desktop (or another Docker engine) with Docker Compose v2
- A GitHub [personal access token](https://github.com/settings/tokens) with `read:packages`
  scope, exported as `GITHUB_TOKEN` (and `GITHUB_ACTOR` set to your GitHub username) — needed to
  resolve `purerestlib` from GitHub Packages. GitHub Packages requires authentication to *read*
  Maven artifacts even from a public repo.

## Quickstart

```
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-PAT-with-read:packages>

docker compose up -d   # starts Postgres
sbt run                # runs migrations, then starts the service on :8080
```

Then, in another terminal:

```
# Create an order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" -d '{"item":"widget","quantity":3}'

# Read it back (substitute the id from the response above)
curl http://localhost:8080/orders/<id>

# Partially update it (quantity/status)
curl -X PATCH http://localhost:8080/orders/<id> \
  -H "Content-Type: application/json" -d '{"quantity":5,"status":"shipped"}'

# Delete it
curl -X DELETE http://localhost:8080/orders/<id>

# Liveness / readiness
curl http://localhost:8080/health
curl http://localhost:8080/health/ready
```

Swagger UI (generated from the same tapir endpoint definitions as the real routes — see
`purerest.docs.Docs`) is browsable at **http://localhost:8080/docs**.

## Testing

```
sbt scalafmtCheck test
```

Unit tests use an in-memory `OrderStore`; Postgres-backed tests spin up a real, ephemeral
container via Testcontainers — no local Postgres or manual setup needed to run `sbt test`.

## One database per service

Unlike purerest's own `order-service` (which uses the pluralized `orders`/`orders` naming), this
repo adopts a **one database per service, named after the domain** convention: a dedicated
Postgres database named `order` (singular), with an `order` table (quoted throughout — `order` is
a reserved PostgreSQL keyword). REST paths stay pluralized (`/orders`, `/orders/{id}`) per
ordinary resource-collection convention; only the database/table naming reflects the
one-db-per-service rule. See [`conductor/tech-stack.md`](./conductor/tech-stack.md) for the full
rationale and the deviations recorded from purerest's original `order-service` (the
inventory-service coupling this repo intentionally doesn't carry over).

## Calling other services with resilience

This reference service doesn't call any other service, so purerest's retry + circuit-breaker
middleware (`purerest.resilience.Resilience.middleware`) isn't wired into `Main`. When you adapt
this template to call a real downstream service, wrap its http4s `Client[F]` with
`Resilience.middleware` before building your own client on top of it — see
[`ClientResilienceExampleSuite`](./src/test/scala/orderservice/examples/ClientResilienceExampleSuite.scala)
for the pattern, tested against a dummy client so it stays correct as purerest evolves.
