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

# Or fully replace it (same required fields as PATCH — this resource has no
# other client-writable ones — but PUT is idempotent full-replace semantics)
curl -X PUT http://localhost:8080/orders/<id> \
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

## Generating a new service from this template

This repo doubles as a [giter8](http://www.foundweekends.org/giter8/) template (`src/main/g8/`)
that renames `order-service` into a new, differently-domained service.

### Prerequisites

Same as above (sbt/JDK, Docker), plus the same `GITHUB_ACTOR`/`GITHUB_TOKEN` — the generated
project also resolves `purerestlib` from GitHub Packages.

### Generate

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

### Known limitation: naive pluralization

REST paths pluralize `domain_name` by appending `s` (`/widgets`, `/invoices`) — this doesn't
handle irregular English plurals (e.g. a domain named `company` generates `/companys`, not
`/companies`). Hand-edit the generated `*Routes.scala`/`*RoutesSuite.scala` path segments if your
domain name needs an irregular plural.

## Adding domain fields with the field-codegen tool

The generated service always ships with a fixed set of fields — `item`/`quantity`/`status` plus
`id`/`createdAt`/`updatedAt` — as a working, runnable example. To add fields specific to your
domain (beyond those), use the `tools/codegen/` tool as a post-generation step: it takes a small
YAML field-spec and rewrites the generated project's case class, DTOs, SQL, store (in-memory +
Postgres), and tests to add each field — every field appears in `create`/`update`/the JSON
response, and is exercised through the generated CRUD test suite (create/get/update/delete, plus
the full-lifecycle test).

### Field-spec format

```yaml
fields:
  - name: color
    type: String
    example: "red"
  - name: weight
    type: Int
    example: "7"
  - name: fragile
    type: Boolean
    example: "true"
  - name: expiresAt
    type: Instant
    example: "2026-06-01T00:00:00Z"
```

- `name` — a camelCase Scala identifier (e.g. `expiresAt`); its Postgres column name is derived by
  converting to snake_case (`expires_at`).
- `type` — one of `String`, `Int`, `Boolean`, `Instant` (`java.time.Instant`, stored as
  `TIMESTAMPTZ`). No other types are supported.
- `example` — a literal value (as a string) used to rewrite existing test call sites whose arity
  changes when the field is added, and to assert on in the full-lifecycle test.

### Running it

```
cd tools/codegen
sbt "runMain codegen.Main /absolute/path/to/widget-service /absolute/path/to/field-spec.yaml"
cd /absolute/path/to/widget-service
sbt scalafmt test   # reformats the tool's inserted lines to this project's style, then verifies
```

`tools/codegen/` is a standalone sbt project (sibling to, not aggregated into, this repo's own
build) — it operates on an already-generated project directory, not on the template itself.

### Limitations

- **Additive only, one-shot** — the tool consumes the g8 template's `codegen:fields:` anchor
  comments as it rewrites each file, so it isn't idempotent: running it twice against the same
  generated project will fail (the anchors are gone after the first run). Generate fresh from the
  template if you need to change the field spec.
- **No per-field visibility** — every field is required and appears in `create`, `update`, and the
  JSON response uniformly. The fixed `item`/`quantity`/`status` fields have asymmetric visibility
  (e.g. `status` is server-defaulted, not client-settable at create) that this tool's field model
  doesn't support — see the backlog item in `conductor/tracks.md` for generalizing this.
- **No optional/nullable fields** — v1 only supports required fields.

## Calling other services with resilience

This reference service doesn't call any other service, so purerest's retry + circuit-breaker
middleware (`purerest.resilience.Resilience.middleware`) isn't wired into `Main`. When you adapt
this template to call a real downstream service, wrap its http4s `Client[F]` with
`Resilience.middleware` before building your own client on top of it — see
[`ClientResilienceExampleSuite`](./src/test/scala/orderservice/examples/ClientResilienceExampleSuite.scala)
for the pattern, tested against a dummy client so it stays correct as purerest evolves.
