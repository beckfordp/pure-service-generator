# Spec: Paginated list endpoint in the generator's base template

## Overview

`pure-service-generator`'s generated CRUD surface is create / get-by-id / update (PATCH) /
replace (PUT) / delete — no `GET /<resource>` to browse the collection. Two generated services
(catalog-service, inventory-service) each hand-added a near-identical paginated list endpoint
(`limit`/`offset` + `X-Total-Count`); order-service added a filtered variant. This track closes
TECHNICAL_DEBT.md TD-4 by putting that proven pattern into the g8 template (`src/main/g8/`) so
every freshly generated service gets it for free. Scope is this repo only — no forced backport
to already-generated services (per TD-4).

## Functional Requirements

1. **Route:** add `GET /<domain_name>s` (same naive pluralization as the existing CRUD paths),
   with optional `limit` and `offset` query params typed as `Option[Int]`.
2. **Pagination semantics:** default `limit = 20`, max `limit = 100`, default `offset = 0`;
   `limit <= 0`, `limit > 100`, or `offset < 0` returns `400` with JSON body
   `{"error": "Invalid pagination parameters"}` (catalog-service's exact validation shape).
   Valid requests return `200` with a JSON array of `$domain_name;format="cap"$Response`.
3. **Total count:** every successful response carries an `X-Total-Count` response header holding
   the total number of rows in the collection (ignoring limit/offset).
4. **Ordering:** newest first — `ORDER BY created_at DESC`.
5. **CORS:** expose `X-Total-Count` on cross-origin responses via
   `Access-Control-Expose-Headers` (add `.withExposeHeadersIn(Set(ci"X-Total-Count"))` to
   `Cors.scala`), so browser JS can read it.
6. **Store API:** extend `$domain_name;format="cap"$Store[F]` with
   `def list(limit: Int, offset: Int): F[(List[$domain_name;format="cap"$], Long)]`.
   - In-memory: sort all entities by `createdAt` descending, `slice(offset, offset + limit)`,
     return total size.
   - Postgres: one page query selecting `id`, all field-spec columns, `created_at`, `updated_at`,
     and `count(*) OVER() AS total_count`, ordered newest-first with `LIMIT`/`OFFSET`; when the
     page is empty (empty table or offset past the end) fall back to a direct `SELECT count(*)` so
     the total is still correct.
7. **Error type:** add `InvalidPagination` to the sealed `$domain_name;format="cap"$Error` trait
   with a dedicated 400 tapir error output; existing endpoints' 404 output unchanged.
8. **Wiring:** include the list endpoint in `$domain_name;format="cap"$Routes.routes` and in
   `Main.scala`'s `Docs.routes` endpoint list (so OpenAPI/Swagger documents it).
9. **Field-codegen compatibility:** implement the SQL/type/constructor parts using the existing
   `codegen:fields:` anchors (`SQL_SELECT_COLUMNS`, `SQL_SELECT_CODEC`, `SQL_SELECT_TUPLE_TYPE`,
   `SQL_SELECT_PATTERN_VARS`, `SQL_SELECT_CONSTRUCT_ARGS`) so a field-spec-populated service gets
   all its columns in list results. No new anchor tags or `tools/codegen/` change is expected. The
   zero-field base entity (base template default) must also compile and work.
10. **Docs:** add a `GET` list example (including `X-Total-Count`) to the template README's API
    walkthrough; update the generator README's "what a generated service includes" surface and
    `conductor/tech-stack.md` where the CRUD surface / CORS policy is described.

## Non-Functional Requirements

- No new third-party dependencies; use existing Skunk / tapir / http4s APIs only.
- Template-only change: `src/main/g8/` plus its own docs — no changes to `tools/codegen/`,
  `scripts/`, or the publish pipeline.
- Follow the repo's existing g8 conventions: `$domain_name$` / `$domain_name;format="cap"$`
  placeholders, `\$` escaping for Skunk SQL params, `sbt scalafmt` in the generated instance.
- TDD: failing tests first, then implementation; coverage >80% for new code (via the regenerated
  scratch instance).

## Acceptance Criteria

- [ ] `./scripts/dev-regenerate.sh` (default zero-field domain) produces a service whose full
  `sbt test` suite passes, including new store/route tests for list: default page, explicit
  `limit`/`offset`, newest-first order, empty table → `200 []` + `X-Total-Count: 0`, offset past
  end → empty page with correct total, invalid `limit`/`offset` → `400` JSON error.
- [ ] `./scripts/dev-regenerate.sh --field-spec <spec>` (entity with several fields, including an
  `Instant` field) also passes its full test suite, proving the codegen anchors render correctly
  in the list query.
- [ ] A real running regenerated instance returns a correct paginated page + `X-Total-Count` for
  `GET /<resource>s?limit=…&offset=…`, `400` for invalid params, and the header is reachable
  cross-origin (`Access-Control-Expose-Headers: X-Total-Count`).
- [ ] Swagger/OpenAPI docs list the new endpoint and header.
- [ ] Template README and `conductor/tech-stack.md` describe the new default list endpoint and
  CORS exposure.
- [ ] Regenerating a fresh instance after porting the template change reproduces the behavior
  (template round-trip per `docs/developing-the-template.md`).

## Out of Scope

- Backporting the list endpoint to the six already-generated services (opportunistic per-service
  work, per TD-4).
- Bulk "delete all" or any other destructive endpoint.
- Filtering, search, sorting options, or cursor-based pagination — this is an unfiltered page of
  the whole collection only.
- Response-body pagination metadata (total/page links); `limit`/`offset` + `X-Total-Count` only.
- Configurable pagination defaults (PureConfig) — constants in code, matching catalog-service.
