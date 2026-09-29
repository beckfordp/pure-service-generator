# Tech Stack

## Language & Build
- **Scala** 3.9.0
- **sbt** — single-module build (unlike purerest's multi-module aggregate: this is one
  standalone microservice project).

## Dependency on purerest
- **purerest** (`io.github.beckfordp` `purerestlib`) is resolved as a **published dependency
  from GitHub Packages**, version-pinned — never a source/`.dependsOn` link back to the
  `purerest` repo. Requires `GITHUB_ACTOR`/`GITHUB_TOKEN` credentials to resolve, matching
  purerest's own `smoke-test/` module's external-consumer setup.

## Effect System
- **Cats Effect 3** — tagless-final, typeclass-based APIs throughout (`F[_]: Async`, etc.).

## HTTP
- **http4s** — server, via purerest's resilient client conventions where applicable.

## API Documentation
- **tapir** — endpoints described once as tapir values; purerest's `purerest.docs.Docs`
  interprets that into both real `HttpRoutes[F]` and a generated OpenAPI spec + Swagger UI.

## JSON
- **circe**, via `http4s-circe`.

## Persistence
- **PostgreSQL** via **Skunk**.
- **Flyway** migrations, run automatically on startup.
- **PureConfig** for `application.conf`-based settings (`key = <default>` + `key = ${?ENV_VAR}`
  override pattern, camelCase-field-to-kebab-case-key convention) — same pattern as purerest's
  own reference services.
- **Testcontainers** (`testcontainers-scala-postgresql`) for integration tests; a separate
  `docker-compose.yml` Postgres container for local/manual dev.
- **One database per service:** a single dedicated Postgres database, named after the domain —
  `order` (singular), not `orders`.

## Observability
- **Tracing**: OpenTelemetry via **otel4s**, via purerest.
- **Logging**: **log4cats** (slf4j backend) + **Logback**, via purerest.
- **Metrics**: otel4s's `Meter[F]`, via purerest.

## Testing
- **munit** + **munit-cats-effect**.

## Formatting
- **scalafmt** — default Scala 3 style.

## Deployment
- Docker image via **sbt-native-packager** (`JavaAppPackaging`, `DockerPlugin`), matching
  purerest's own reference services' packaging.

## Known Constraints
- **No generator machinery yet.** This repo currently holds one hand-adapted reference service,
  not a parameterized template — see `product.md`'s Non-Goals/Future Direction.
- **No inventory-service coupling, no resilience wiring (2026-09-29 deviation).** order-service's
  `POST /orders` calls a live inventory-service over HTTP at runtime to reserve stock
  (`InventoryClient`), and purerest's retry/circuit-breaker resilience middleware exists in
  order-service solely to wrap that one outbound call. Both conflict with this repo's
  standalone, Postgres-only footprint, so both are dropped in the port: `POST /orders` persists
  directly, no `reservation_id`/`reserved_quantity` columns, no outbound HTTP client, no
  resilience middleware. See `conductor/tracks/pure-service-generator_20260929/spec.md`'s
  "Deviations from order-service" for the full reasoning.
