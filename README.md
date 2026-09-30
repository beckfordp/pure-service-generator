# pure-service-generator

A generator for new [purerest](https://github.com/beckfordp/purerest)-based microservices: point
it at a domain name (and, optionally, a field-spec), and it produces a production-quality,
fully-tested Scala 3 / Cats Effect / http4s / Skunk service — full CRUD, health checks, Postgres
persistence, observability, and CI — ready to push as its own repo.

The generator is a [giter8](http://www.foundweekends.org/giter8/) template (`src/main/g8/`) plus
two companion tools: [`tools/codegen/`](./tools/codegen/README.md) (adds domain fields to a
generated service) and `scripts/generate-and-publish-service.sh` (chains generation → field
codegen → a new GitHub repo → CI, in one command). See
[`conductor/product.md`](./conductor/product.md) for the vision and
[`conductor/tracks.md`](./conductor/tracks.md) for in-progress/planned work.

## Prerequisites

- sbt / JDK 21
- Docker Desktop (or another Docker engine) with Docker Compose v2 — for the generated service's
  Postgres-backed tests
- A GitHub [personal access token](https://github.com/settings/tokens) with `read:packages`
  scope, exported as `GITHUB_TOKEN` (and `GITHUB_ACTOR` set to your GitHub username) — needed to
  resolve `purerestlib` from GitHub Packages. GitHub Packages requires authentication to *read*
  Maven artifacts even from a public repo.

## Generating a new service

```
sbt new file:///absolute/path/to/pure-service-generator --domain_name=widget
```

If your sbt version supports resolving local `file://` templates directly, that's all you need —
follow the interactive prompts (or pass `--package=...` too, see below) and it'll scaffold a new
`widget-service/` directory. If instead you see `Template not found for: file://...`, your sbt's
built-in `new` command only resolves a hardcoded list of GitHub template shortcuts, not arbitrary
`file://` URIs — use giter8's own launcher library directly instead, as a project-local dependency
in a throwaway sbt project (this changes nothing about your global sbt setup):

```
mkdir -p /tmp/g8-out/project
echo 'sbt.version=1.13.0' > /tmp/g8-out/project/build.properties
echo 'libraryDependencies += "org.foundweekends.giter8" %% "giter8-launcher" % "0.18.0"' > /tmp/g8-out/build.sbt
cd /tmp/g8-out
sbt "runMain giter8.LauncherMain file:///absolute/path/to/pure-service-generator --domain_name=widget -o widget-service"
```

### Properties

- `domain_name` (default `widget`) — lowercase, singular (e.g. `widget`, `invoice`). Drives the
  sbt project/Docker image name, the Postgres database/table name, REST paths (see the
  pluralization caveat below), Scala class/identifier names, and the `service-name` config
  default.
- `package` (default `$domain_name$service`, e.g. `widgetservice`) — the Scala package.
  Independently overridable, including reverse-domain style (`--package=com.example.widgetservice`
  generates nested `com/example/widgetservice/` directories — giter8's built-in behavior for a
  property literally named `package`).

### After generating

```
cd widget-service
sbt scalafmt test   # one reformat pass is expected: identifier-length differences
                     # (e.g. "Widget" vs "Order") shift line-wrapping vs. this repo's own
docker compose up -d && sbt run
```

The generated service's base entity is just `id`/`createdAt`/`updatedAt` — it has no
domain-specific fields (and so a genuinely empty `create`/`update` body) until you add some via
the field-codegen tool below.

### Known limitation: naive pluralization

REST paths pluralize `domain_name` by appending `s` (`/widgets`, `/invoices`) — this doesn't
handle irregular English plurals (e.g. a domain named `company` generates `/companys`, not
`/companies`). Hand-edit the generated `*Routes.scala`/`*RoutesSuite.scala` path segments if your
domain name needs an irregular plural.

## Adding domain entity fields

Every domain-specific domain field beyond the base `id`/`createdAt`/`updatedAt` entity is added via a
small YAML field-spec and the `tools/codegen/` tool — see
[`tools/codegen/README.md`](./tools/codegen/README.md) for the field-spec format, how to run it,
and its limitations.

## Automated generate → publish → CI pipeline

`scripts/generate-and-publish-service.sh` chains everything above into one command: generate a
service from this repo's giter8 template, optionally apply a field-spec, reformat it, create a
new **public** GitHub repo under your account, push, wire up CI credentials, and wait for that
first GitHub Actions run to go green.

```
./scripts/generate-and-publish-service.sh --domain-name widget \
  [--package <package>] [--field-spec <path>] [--repo-name <name>]
```

- `--domain-name` (required) — same as the giter8 `domain_name` property.
- `--package` (optional) — passed through to giter8.
- `--field-spec` (optional) — a field-spec YAML path; if given, the field-codegen tool runs
  against the generated service before it's pushed (see "Adding domain fields" above).
- `--repo-name` (optional) — defaults to `<domain-name>-service`. The script aborts cleanly,
  without touching anything, if a repo with that name already exists.

### Prerequisites

- `gh`, authenticated (`gh auth status`) with a token that has `repo` and `workflow` scopes (to
  create repos, push, and manage Actions secrets) and `read:packages` scope (the same token is
  reused as the generated repo's own CI credential — see below).
- Everything the template itself needs: sbt/JDK, Docker, `GITHUB_ACTOR`/`GITHUB_TOKEN` in the
  environment (see "Prerequisites" above).

### Why a `GH_PACKAGES_TOKEN` secret

The generated repo's CI workflow (`.github/workflows/ci.yml`, templated into `src/main/g8/`)
needs to resolve `purerestlib` from GitHub Packages, published from a *different* repo
(`beckfordp/purerest`). GitHub Actions' automatic, built-in `GITHUB_TOKEN` is scoped to the
current repo only and can't read another repo's packages, so the script sets a real repository
secret instead — `gh secret set GH_PACKAGES_TOKEN`, reusing the caller's own `gh auth token` —
*before* the push that triggers the first CI run.

### Visibility

Repos are created **public** by default; there's no `--private` flag yet (see Non-Goals in
`conductor/product.md`).

## Developing the template

`src/main/g8/`'s files are giter8 templates, not plain Scala/SQL — you can't type-check or run
them directly. See [`docs/developing-the-template.md`](./docs/developing-the-template.md) for
the generate → edit → diff → port-back → regenerate workflow and its two helper scripts
(`scripts/dev-regenerate.sh`, `scripts/dev-diff.sh`).

## One database per service

Every service the generator produces owns exactly one dedicated Postgres database, named after
its domain — singular, e.g. a `widget` domain gets a `widget` database and an `widget` table
(quoted throughout, since some plausible domain names like `order`, `user`, or `group` are
reserved PostgreSQL keywords). REST paths stay pluralized (`/widgets`, `/widgets/{id}`) per
ordinary resource-collection convention; only the database/table naming reflects the
one-db-per-service rule. See [`conductor/tech-stack.md`](./conductor/tech-stack.md) for the full
rationale.

## Calling other services with resilience

The generated service doesn't call any other service by default, so purerest's retry +
circuit-breaker middleware (`purerest.resilience.Resilience.middleware`) isn't wired into
`Main`. When you adapt a generated service to call a real downstream service, wrap its http4s
`Client[F]` with `Resilience.middleware` before building your own client on top of it — every
generated service ships a tested, PureConfig-driven example of the pattern at
`src/test/scala/$package$/examples/ClientResilienceExampleSuite.scala` (see
[`src/main/g8/src/test/scala/$package$/examples/ClientResilienceExampleSuite.scala`](./src/main/g8/src/test/scala/$package$/examples/ClientResilienceExampleSuite.scala)
in the template), tested against a dummy client so it stays correct as purerest evolves.
