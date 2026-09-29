# Plan: Make the base entity fields field-spec-driven

## Phase 1: Strip the g8 template to zero domain fields
Infrastructure, not Red/Green — verified by generation + local checks, like field-codegen's own
Phase 1.
- [x] Task: Remove `item`/`quantity`/`status` entirely from every `src/main/g8/` file (case
      class, `Create*Request`/`Update*Request`/`*Response`, SQL migration/queries, in-memory +
      Postgres store bodies, all test files) — the anchors stay, but now some sit at the *start*
      of an otherwise-empty list (e.g. `create(/* codegen:fields:CREATE_PARAMS */)`) rather than
      always having `item`/`quantity` already present before them. `0a6b400`

      Also added a new `DEFAULT_VALUE_DECLS` anchor (own-line, statement-mode) replacing the
      hardcoded `defaultStatus` val, for Phase 3 to render `server-defaulted` fields' default
      constants into.
- [x] Task: Regenerate a `widget` service via giter8 (no field-spec applied) and confirm it
      still compiles and `sbt scalafmtCheck test` passes — a structurally empty
      `CreateRequest`/`UpdateRequest`/entity (just `id`/`createdAt`/`updatedAt`) is a valid,
      if degenerate, service. `0a6b400`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Strip the g8 template to zero domain
      fields' (Protocol in workflow.md) - verified directly (prompting off): regenerated
      widget-service compiles (main+test), scalafmt-clean, 52/52 tests pass with a genuinely
      zero-domain-field entity.

## Phase 2: Extend the field-spec format
- [x] Task: Write failing tests (Red) — `FieldSpecParser` parsing the new optional `visibility`
      key (`create-and-update`/`create-only`/`server-defaulted`, defaulting to
      `create-and-update`) and the `default` key (required only for `server-defaulted`,
      rejected otherwise); confirm they fail (no model/validation yet). `fbf18e8`
- [x] Task: Implement (Green) — extend `Field`/`FieldType` with a `Visibility` enum and
      `default: Option[String]`, plus the new validation rules. Run the suite, confirm green.
      `fbf18e8`
- [x] Task: Conductor - User Manual Verification 'Phase 2: Extend the field-spec format'
      (Protocol in workflow.md) - verified directly (prompting off): 43/43 tests green (9 new),
      scalafmt clean; the pre-existing simple (no-visibility) field-specs still parse
      unchanged, confirming backward compatibility.

## Phase 3: Rework the transformation engine for per-field visibility
The core of this track — two related changes to `AnchorTransformer`/`FieldRenderers`.
- [x] Task: Write failing tests (Red) — (a) a "bare list start" fixture (an inline marker with
      *no* preceding items, e.g. `create(/* MARKER */)`, and an own-line marker whose preceding
      line ends in an opening bracket) asserting the first rendered field gets no leading comma
      and no corrupted preceding-line comma-fixup; (b) per-tag visibility filtering (e.g.
      `CREATE_PARAMS` renders only `create-and-update`/`create-only` fields, `UPDATE_PARAMS`
      only `create-and-update`/`server-defaulted`, in field-spec declaration order); (c)
      `server-defaulted` fields render a `default<Field>` constant reference in
      `CONSTRUCT_ARGS`/`SQL_INSERT_TUPLE_ARGS` instead of a create-param reference. Confirm all
      fail. `9e0c01c`
- [x] Task: Implement (Green) — the bare-list-start join style, the per-tag visibility filter
      table, and `default<Field>` constant generation/declaration. Run the suite, confirm green.
      `9e0c01c`

      Generalized further than originally scoped: rather than a special-cased "bare list start"
      style, the join style (leading/trailing/bare/SQL-keyword-preceded/fixed-non-empty-suffix)
      is auto-detected from the literal text on both sides of every marker, covering the
      zero-rendered-fields case (e.g. every field is `create-only`, so `UPDATE_PARAMS` has
      nothing to add) uniformly rather than as a separate mechanism.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Rework the transformation engine for
      per-field visibility' (Protocol in workflow.md) - verified directly (prompting off):
      59/59 tests green (26 new) on the first real run, scalafmt clean; all pre-existing tests
      (backward compatibility) still pass unchanged.

## Phase 4: Regression-check the simple (no-visibility) case
- [x] Task: Re-run `tools/codegen`'s existing test suite and `scripts/verify-codegen-tool.sh`
      unchanged (a field-spec with no `visibility` key at all) against the now-field-less
      template; fix anything the zero-base-fields change broke; confirm green — proves no
      regression for the common case. `7450788`

      Surfaced a real bug: `SQL_SELECT_CODEC`'s Skunk-combinator join was inferred from its
      prefix ending in `*:`, which no longer holds with zero base fields
      (`.query(/* MARKER */timestamptz ...)` has nothing preceding it) — silently fell back to
      comma-joining, producing a type error (`Decoder` required, `Codec` tuple found). Fixed by
      marking the tag as always-combinator rather than inferring it.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Regression-check the simple case'
      (Protocol in workflow.md) - verified directly (prompting off): 60/60 tool tests green,
      then `scripts/verify-codegen-tool.sh` run live end-to-end (9 files rewritten, no
      unresolved anchors, scalafmtCheck + 52/52 tests pass).

## Phase 5: Dogfood verification
- [ ] Task: Write a field-spec expressing `item` (`create-only`), `quantity`
      (`create-and-update`), and `status` (`server-defaulted`, default `"created"`); run it
      through the real pipeline (giter8 generate → field-codegen → `sbt scalafmt` →
      `scalafmtCheck test`) and confirm full CRUD parity with today's archived reference service
      (52/52 tests, same endpoint behavior).
- [ ] Task: Re-run `scripts/verify-g8-template.sh` and `scripts/generate-and-publish-service.sh`
      (live, per this project's established pattern) against the changed template; fix anything
      that breaks.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Dogfood verification' (Protocol in
      workflow.md)

## Phase 6: Documentation
- [ ] Task: Update the README's "Adding domain fields" section (the new `visibility`/`default`
      keys, the three categories) and "Generate"/"Quickstart" sections (a freshly-generated
      service now has no CRUD fields until a field-spec is applied).
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Documentation' (Protocol in
      workflow.md)
