# Spec: Make the base entity fields field-spec-driven

## Overview
Generalizes field-codegen so the g8 template ships with **zero** hardcoded domain fields
(`item`/`quantity`/`status` are removed entirely, leaving only `id`/`createdAt`/`updatedAt`), and
every domain field — including what used to be the "fixed" ones — is supplied via the field-spec.
This requires the field-spec to express *how* a field participates in create/update (previously
assumed uniform), since `item`/`quantity`/`status` each have different create/update visibility
that the current field-codegen v1 model can't express.

## Functional Requirements
1. **Field-spec format**: add an optional `visibility` key per field (default `create-and-update`
   if omitted — backward compatible with every existing field-spec, including the one in the
   README):
   - `create-and-update` (today's only behavior, e.g. `quantity`) — client sets it at create,
     can change it via update.
   - `create-only` (e.g. `item`) — client sets it at create, immutable after; excluded from
     `UpdateRequest`/update SQL/update test call sites.
   - `server-defaulted` (e.g. `status`) — requires a `default` key (a literal of the field's
     type); excluded from `CreateRequest`/insert-as-param/create test call sites, inserted using
     the default value instead; settable via update like `create-and-update`.
2. **Strip the g8 template**: remove `item`/`quantity`/`status` entirely from every file in
   `src/main/g8/` (case class, DTOs, SQL, in-memory/Postgres stores, tests, migration) — the
   template's base entity becomes just `id`/`createdAt`/`updatedAt`. `CreateXRequest`/
   `UpdateXRequest` become structurally empty (zero required fields) until a field-spec is
   applied.
3. **Rework `AnchorTransformer`/`FieldRenderers`**: every anchor's cell-rendering now filters the
   field-spec's fields by which visibility category applies to that anchor (e.g.
   `CREATE_PARAMS`/`TEST_CREATE_ARGS` only render `create-and-update`/`create-only` fields;
   `UPDATE_PARAMS`/`TEST_UPDATE_ARGS` only render `create-and-update`/`server-defaulted` fields),
   while preserving each field's declaration order within the filtered set.
4. **`server-defaulted` code generation**: a `private val default<Field> = <literal>` constant
   (matching today's `defaultStatus` pattern) used in both in-memory and Postgres `create`.

## Non-Functional Requirements
- No change to the anchor *marker* mechanism itself (own-line/inline auto-detection, join-style
  auto-detection) — only to which fields get filtered into each anchor's rendering, plus a new
  "bare list start" join style for anchors that may now have no preceding items at all.
- Existing field-spec files/examples (field-codegen_20260929's README example, this track's own
  test fixtures) continue to work unchanged, since `visibility` is optional.

## Acceptance Criteria
- Expressing `item` (`create-only`), `quantity` (`create-and-update`), and `status`
  (`server-defaulted`, default `"created"`) as a field-spec, applied to the now-field-less
  template, produces a generated service with full CRUD parity to today's hardcoded behavior —
  same endpoints, same test suite passing (52/52, matching the archived reference service).
- A field-spec with no `server-defaulted`/`create-only` fields (i.e. everything
  `create-and-update`) behaves identically to field-codegen_20260929's current behavior — no
  regression for the simple case.

## Out of Scope
- Optional/nullable fields (still deferred, per field-codegen_20260929's own Out of Scope).
- A 4th, fully-read-only/computed visibility category.
- Changing how giter8 generation and field-codegen relate (field-codegen remains a separate,
  manual post-generation step — not folded into `sbt new`/`giter8.LauncherMain`).
