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
- **http4s** — server only. No outbound HTTP client in the live service (see "No
  inventory-service coupling" below); `ClientResilienceExampleSuite` demonstrates purerest's
  resilient-client pattern for whoever adapts this template to add one.

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

## Code Generation
- **giter8** (`src/main/g8/`) templates the entire reference service, parameterized by
  `domain_name` (lowercase, singular — drives naming/DB/REST paths) and an independently-settable
  `package` (defaults to `$domain_name$service`; giter8's built-in dot-to-slash conversion for a
  property literally named `package` supports reverse-domain nesting). Generated via
  `giter8-launcher` (see "giter8 gotchas" below), not sbt's built-in `new` command. See README's
  "Generating a new service from this template".

## Known Constraints
- **Parametrized field codegen and generation-time CI/CD wiring not built yet.** The giter8
  template (see "Code Generation" above) covers naming/package renaming only — an entity-field
  list expanding into a case class/migration/codec, and a generate → repo-create → push → CI
  pipeline, remain future work. See `product.md`'s Non-Goals/Future Direction.
- **giter8 gotchas (discovered 2026-09-29, building the template).** (1) giter8's
  capitalize-first-letter format name is lowercase `cap`, not `Cap` — an unrecognized format name
  is silently ignored (falls back to the raw value) rather than erroring, so this only surfaces by
  actually generating output and inspecting it. (2) Every literal `$` in template file content —
  including Scala string interpolation (`${expr}`, `$id`) and Skunk's own `$`-based SQL parameter
  interpolation (`$uuid`, `$text`) — must be escaped as `\$`, or generation aborts with "An
  unexpected error occurred while processing the template." (3) This sbt version's built-in `new`
  command only resolves a small hardcoded list of GitHub template shortcuts, not arbitrary
  `file://` URIs — use giter8's own `giter8-launcher` library directly instead (a project-local,
  not global, dependency — see README for the exact command).
- **No inventory-service coupling (2026-09-29 deviation).** order-service's `POST /orders` calls
  a live inventory-service over HTTP at runtime to reserve stock (`InventoryClient`), which
  conflicts with this repo's standalone, Postgres-only footprint. Dropped in the port:
  `POST /orders` persists directly, no `reservation_id`/`reserved_quantity` columns, no outbound
  HTTP client in `Main`/the live service. See
  `conductor/tracks/pure-service-generator_20260929/spec.md`'s "Deviations from order-service"
  for the full reasoning.
- **`sbt bgRun` hangs when scripted (discovered 2026-09-29).** `sbt --no-server bgRun "shell"`
  backgrounded from a verify script (the pattern purerest's own scripts use) hung indefinitely
  here with no log output — plain foreground `sbt run`, backgrounded at the shell level
  (`sbt run > log 2>&1 & PID=$!`), boots cleanly instead (migrations run, server binds to 8080)
  and can be `kill`ed normally. Verify scripts in this repo use the latter.
- **Resilience (retry + circuit breaker) kept only as a tested example, not live wiring
  (amended 2026-09-29).** With `InventoryClient` gone there's no outbound call in the live
  service to wrap purerest's `Resilience.middleware` around — but since this repo exists to be
  copied/adapted into services that *will* call others, the pattern is kept as a standalone,
  CI-verified example test (`ClientResilienceExampleSuite`, wrapping a dummy `Client[F]`) rather
  than dropped outright. See README's "Calling other services with resilience".
