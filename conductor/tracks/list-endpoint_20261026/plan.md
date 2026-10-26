# Plan: Paginated list endpoint in the generator's base template

## Phase 1: Implement in a scratch instance (TDD), then port to the g8 template

- [ ] Task: Regenerate the scratch instance (`./scripts/dev-regenerate.sh --domain-name listcheck`)
      and confirm the baseline `sbt test` is green.
- [ ] Task: **Red** — add failing tests to the scratch instance:
  - `ListcheckStoreSuite`: `list` returns newest-first pages with correct totals; empty store →
    `(Nil, 0)`; offset past end → empty page, correct total.
  - `ListcheckStorePostgresSuite`: same against a real Postgres container (seed via `create`,
    assert order/page/total).
  - `ListcheckRoutesSuite`: `GET /listchecks` default page (20) + `X-Total-Count`; explicit
    `limit`/`offset`; newest-first order; empty table → `200 []`, `X-Total-Count: 0`; offset past
    end; `limit=0`/`limit=101`/`offset=-1` → `400` with JSON error body.
  - `CorsSuite`: cross-origin GET response exposes `X-Total-Count` via
    `Access-Control-Expose-Headers`.
  - `ListcheckDocsSuite`: docs YAML mentions the list path and `X-Total-Count`.
  - Run `sbt test`; confirm failures are exactly the missing `list` method / `InvalidPagination` /
    route / expose-header (Red phase).
- [ ] Task: **Green** — implement in the scratch instance:
  - `ListcheckError.scala`: add `InvalidPagination` to the sealed trait.
  - `ListcheckStore.scala`: add `def list(limit: Int, offset: Int): F[(List[Listcheck], Long)]`;
    in-memory newest-first slice; Postgres `selectListcheckPage` (`id`, field columns,
    `created_at`, `updated_at`, `count(*) OVER() AS total_count`, `ORDER BY created_at DESC`,
    `LIMIT`/`OFFSET`) with `countListcheck` fallback on an empty page; map rows and integrate into
    `timed(...)`.
  - `ListcheckRoutes.scala`: `listListchecksEndpoint` (query `Option[Int]` limit/offset,
    `X-Total-Count` header, 400 `invalidPaginationOutput`), `defaultLimit = 20` /
    `maxLimit = 100`, `listListchecksServerEndpoint`, register in `routes`.
  - `Cors.scala`: `.withExposeHeadersIn(Set(ci"X-Total-Count"))`.
  - `Main.scala`: add list endpoint to `Docs.routes` endpoint list.
  - Run `sbt test`; all green. Then `sbt scalafmt test` and `sbt coverage test coverageReport`;
    confirm list code covered.
- [ ] Task: Port the scratch diff back into `src/main/g8/` by hand (per
      `docs/developing-the-template.md`): re-instate `$domain_name$` /
      `$domain_name;format="cap"$` / `$package$` placeholders, `codegen:fields:` anchors
      (`SQL_SELECT_COLUMNS`, `SQL_SELECT_CODEC`, `SQL_SELECT_TUPLE_TYPE`,
      `SQL_SELECT_PATTERN_VARS`, `SQL_SELECT_CONSTRUCT_ARGS`) instead of literal columns, and `\$`
      escaping for Skunk SQL/interpolation. Files: `Error.scala`, `Store.scala`, `Routes.scala`,
      `Cors.scala`, `Main.scala`, `README.md`, and the four test suites. No changes to
      `tools/codegen/`.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Implement in a scratch instance (TDD),
      then port to the g8 template' (Protocol in workflow.md) — prompting is off for this repo
      (`.claude/prompt-off`); verify via: fresh `./scripts/dev-regenerate.sh` (zero-field) full
      `sbt test` green; then `./scripts/dev-regenerate.sh --domain-name listcheckfields
      --field-spec <spec with String/Int/Boolean/Instant fields>` full `sbt test` green, proving
      anchor rendering.

## Phase 2: Live end-to-end verification & documentation

- [ ] Task: Write `scripts/verify-list-endpoint.sh` (modeled on `verify-cors.sh`): regenerate a
      scratch instance, start Postgres + service, POST several entities, then assert with `curl`:
      default paging returns 20 newest-first; `?limit=2&offset=1` returns the expected page;
      `X-Total-Count` matches the seeded total; `limit=0` / `offset=-1` → `400`; CORS response
      carries `Access-Control-Expose-Headers: X-Total-Count`. Run it.
- [ ] Task: Update docs — template `README.md` (add `GET /<resource>s?limit=&offset=` example and
      `X-Total-Count` note in the API walkthrough), generator `README.md` (CRUD surface now
      includes list), `conductor/tech-stack.md` (list endpoint + CORS expose header).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Live end-to-end verification &
      documentation' (Protocol in workflow.md) — run `./scripts/verify-list-endpoint.sh` and
      review the docs diffs; no interactive walkthrough (prompting off).

## Phase 3: Gluon cross-repo checkoff

- [ ] Task: In `gluon/PLAN.md`, mark Rework 6 `— DONE` and tick TD-4.1; in
      `gluon/TECHNICAL_DEBT.md`, tick TD-4.1. Commit in the gluon repo (separate commit,
      user-approved).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Gluon cross-repo checkoff' (Protocol in
      workflow.md) — confirm PLAN.md/TECHNICAL_DEBT.md reflect the completed Rework 6.
