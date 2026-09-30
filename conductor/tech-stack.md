# Tech Stack

## Language & Build
- **Scala** 3.9.0
- **sbt** — each generated service is a single-module build (unlike purerest's multi-module
  aggregate). `tools/codegen/` is a separate, independent single-module build of its own.

## Dependency on purerest
- **purerest** (`io.github.beckfordp` `purerestlib`) is resolved as a **published dependency
  from GitHub Packages**, version-pinned — never a source/`.dependsOn` link back to the
  `purerest` repo. Requires `GITHUB_ACTOR`/`GITHUB_TOKEN` credentials to resolve, matching
  purerest's own `smoke-test/` module's external-consumer setup.

## Effect System
- **Cats Effect 3** — tagless-final, typeclass-based APIs throughout (`F[_]: Async`, etc.).

## HTTP
- **http4s** — server only. No outbound HTTP client in a freshly generated service (see "No
  inventory-service coupling" below); `ClientResilienceExampleSuite` (in the template) demonstrates
  purerest's resilient-client pattern for whoever adapts a generated service to add one.

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
  singular (e.g. a `widget` domain gets a `widget` database), not the pluralized form.

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
- **giter8** (`src/main/g8/`) templates a full production-quality microservice, parameterized by
  `domain_name` (lowercase, singular — drives naming/DB/REST paths) and an independently-settable
  `package` (defaults to `$domain_name$service`; giter8's built-in dot-to-slash conversion for a
  property literally named `package` supports reverse-domain nesting). Generated via
  `giter8-launcher` (see "giter8 gotchas" below), not sbt's built-in `new` command. See README's
  "Generating a new service from this template". The template's base entity is just
  `id`/`createdAt`/`updatedAt` — zero domain fields until field-codegen runs.
- **field-codegen** (`tools/codegen/`) — a standalone sbt project (own build, not aggregated into
  the root build) that adds every domain field to a generated service from a YAML
  field-spec (`circe-yaml` for parsing). Rewrites every insertion point via `codegen:fields:<TAG>`
  anchor comments embedded in the g8 template — chosen over pattern-matching Scala/SQL shape
  since this repo controls both ends. Own-line vs. inline placement and the join style (leading
  comma, trailing comma, bare-list-start, SQL-keyword-preceded, Skunk's `*:` combinator) are
  auto-detected from the surrounding text (one hardcoded exception: `SQL_SELECT_CODEC` is always
  combinator-mode, since a bare marker there has no `*:` text nearby to infer from). An optional
  per-field `visibility` (`create-and-update`/`create-only`/`server-defaulted`, defaulting to
  the first) lets a field-spec express asymmetric create/update participation. See README's
  "Adding domain fields with the field-codegen tool".
- **generate-and-publish-service.sh** (`scripts/`) — chains giter8 generation, optional
  field-codegen, an `sbt scalafmt` reformat pass, `gh repo create --public`, a
  `gh secret set GH_PACKAGES_TOKEN` (reusing the caller's own `gh auth token`, set *before* the
  push that triggers the first CI run), and `gh run watch --exit-status` to confirm that run
  passes. See README's "Automated generate → publish → CI pipeline".
- **dev-regenerate.sh / dev-diff.sh** (`scripts/`) — generates a disposable, compiler-checked
  scratch instance (`.dev/<domain-name>-service`, gitignored) plus a frozen `.baseline` sibling
  from the same generation, for developing the template itself (its placeholder-laden files
  can't be type-checked directly). `dev-diff.sh` diffs the two — plain output only, no
  auto-patching of the template (reverse-mapping instantiated text back onto
  `$domain_name$`/`$package$`/`codegen:fields:` placeholders is inherently ambiguous). See
  `docs/developing-the-template.md`.

## CI (generated services)
- **GitHub Actions** (`.github/workflows/ci.yml`, templated into `src/main/g8/`) —
  `actions/checkout` + `actions/setup-java` (temurin 21, matching the packaging base image) +
  `sbt/setup-sbt`, then `sbt scalafmtCheck Test/scalafmtCheck test` on push/PR to `main`.
  `GITHUB_TOKEN` comes from a `GH_PACKAGES_TOKEN` repository secret rather than the automatic,
  same-repo-scoped built-in `GITHUB_TOKEN`, since the generated build needs to resolve
  `purerestlib`'s GitHub Packages from a *different* repo (`beckfordp/purerest`).

## Known Constraints
- **dev-regenerate.sh's `.baseline` must be frozen after formatting, not before
  (2026-09-30, generator-reframe track).** Copying it before `sbt scalafmt` ran made
  `dev-diff.sh` show scalafmt's own reformatting as a spurious diff even with zero hand-edits —
  found via live testing, fixed by reordering: format → freeze baseline → test.
- **The publish pipeline creates public repos only (2026-09-29).** No `--private` flag yet —
  see `product.md`'s Non-Goals/Future Direction.
- **Generated services need an `sbt scalafmt` pass before their first commit (confirmed via
  live testing, 2026-09-29).** Long identifier names (from a long `domain_name`) shift
  line-wrapping vs. this repo's own scalafmt baseline (same root cause as the g8-template
  track's "one reformat pass is expected" note) — skipping it makes the generated CI's own
  `scalafmtCheck` fail on the very first push. `generate-and-publish-service.sh` runs this pass
  automatically; anyone generating and committing by hand should too (see README).
- **field-codegen is additive-only and not idempotent (2026-09-29).** It consumes each file's
  anchor comments as it rewrites them, so running it twice against the same generated project
  fails on the second run.
- **field-codegen's join-style auto-detection required two live-testing-surfaced fixes
  (2026-09-29, base-fields-spec track).** Once the template's fixed fields were removed, some
  anchors could render zero fields with a fixed non-empty remainder still needing separation
  (e.g. a bare `SQL_UPDATE_TUPLE_TYPE` marker followed directly by `UUID)`) — handled by
  detecting SQL keywords (`SELECT`/`SET`/`RETURNING`/`VALUES`) as list-openers alongside opening
  brackets. `SQL_SELECT_CODEC`'s Skunk-combinator join could no longer always be inferred from
  its prefix either, once nothing reliably precedes it — fixed by marking that one tag
  explicitly rather than inferring it.
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
  and can be `kill`ed normally.
- **Resilience (retry + circuit breaker) kept only as a tested example, not live wiring
  (amended 2026-09-29).** A freshly generated service has no outbound call to wrap purerest's
  `Resilience.middleware` around — but since generated services are meant to be adapted into
  ones that *will* call others, the pattern is kept as a standalone, CI-verified example test in
  the template (`ClientResilienceExampleSuite`, wrapping a dummy `Client[F]`) rather than
  dropped outright. See README's "Calling other services with resilience".
