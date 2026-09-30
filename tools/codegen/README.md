# field-codegen

Extends a service generated from this repo's giter8 template (`src/main/g8/`) with domain
fields. A generated service's base entity is just `id`/`createdAt`/`updatedAt` — every
domain-specific field is added via this tool as a post-generation step: it takes a small YAML
field-spec and rewrites the generated project's case class, DTOs, SQL, store (in-memory +
Postgres), and tests to add each field, and is exercised through the generated CRUD test suite
(create/get/update/delete, plus the full-lifecycle test).

See the main [`README.md`](../../README.md) for generating a service in the first place.

## Field-spec format

```yaml
fields:
  - name: item
    type: String
    example: "widget"
    visibility: create-only
  - name: quantity
    type: Int
    example: "4"
  - name: status
    type: String
    example: "shipped"
    visibility: server-defaulted
    default: "created"
```

- `name` — a camelCase Scala identifier (e.g. `expiresAt`); its Postgres column name is derived by
  converting to snake_case (`expires_at`).
- `type` — one of `String`, `Int`, `Boolean`, `Instant` (`java.time.Instant`, stored as
  `TIMESTAMPTZ`). No other types are supported.
- `example` — a literal value (as a string) used to rewrite existing test call sites whose arity
  changes when the field is added, and to assert on in the full-lifecycle test.
- `visibility` (optional, default `create-and-update`) — how the field participates in
  `create`/`update`:
  - `create-and-update` — the client sets it at create, and can change it via update (the
    default).
  - `create-only` — the client sets it at create; immutable after (excluded from `UpdateRequest`
    and update SQL).
  - `server-defaulted` — the server assigns a `default` value at create (never client-settable
    there — excluded from `CreateRequest`); settable via update like `create-and-update`.
- `default` — required for, and only valid on, `server-defaulted` fields: a literal (of `type`)
  used for the generated `private val default<Field>` constant.

## Running it

```
cd tools/codegen
sbt "runMain codegen.Main /absolute/path/to/widget-service /absolute/path/to/field-spec.yaml"
cd /absolute/path/to/widget-service
sbt scalafmt test   # reformats the tool's inserted lines to this project's style, then verifies
```

`tools/codegen/` is a standalone sbt project (sibling to, not aggregated into, this repo's own
build) — it operates on an already-generated project directory, not on the template itself.

## Limitations

- **Additive only, one-shot** — the tool consumes the g8 template's `codegen:fields:` anchor
  comments as it rewrites each file, so it isn't idempotent: running it twice against the same
  generated project will fail (the anchors are gone after the first run). Generate fresh from the
  template if you need to change the field spec.
- **No optional/nullable fields** — v1 only supports required fields.
