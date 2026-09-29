# Plan: Parametrized field-codegen (entity-field list → case class + migration + codec)

## Phase 1: Add stable anchor comments to the g8 template
Key design decision: since this repo controls both ends (the template's generated code AND the
codegen tool), embedding stable `// codegen:fields`-style anchor comments at each insertion point
is far more robust than pattern-matching against Scala/SQL code shape. This phase is
infrastructure, not new runtime behavior - verified by "still compiles/passes", not Red/Green.
- [x] Task: Add anchor comments to `src/main/g8/` template files at every insertion point (domain
      case class, `*Store` trait + in-memory/Postgres bodies, SQL query strings, migration
      `CREATE TABLE`, `Create*Request`/`Update*Request`/`*Response` DTOs, route handler
      pass-throughs) and to the test files whose call sites the tool will rewrite. `e51840e`
- [x] Task: Regenerate a `widget` service and confirm `sbt scalafmtCheck test` still passes with
      the (inert) anchor comments present - proves this phase didn't regress the template.
      `e51840e`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Add stable anchor comments to the g8
      template' (Protocol in workflow.md) - verified directly (prompting off): fresh
      `giter8.LauncherMain` regeneration of `widget-service`, `scalafmtCheck` clean after
      `sbt scalafmt`, `sbt test` 52/52 passed. 26/26 designed anchor tags confirmed present via
      catalog sweep.

## Phase 2: Scaffold the codegen tool and field-spec parsing
- [x] Task: Write failing tests (Red) - `tools/codegen/` munit tests for parsing a YAML
      field-spec (name/type/example list) into a typed `Field` model; confirm they fail to
      compile (no parser yet). `c2977da`
- [x] Task: Implement (Green) - scaffold `tools/codegen/` as its own sbt project (sibling to,
      not aggregated into, the reference service's build), using `circe-yaml` (consistent with
      this project's existing circe usage) to parse into `Field(name, type, example)`. Run the
      suite, confirm green. `c2977da`
- [x] Task: Conductor - User Manual Verification 'Phase 2: Scaffold the codegen tool and
      field-spec parsing' (Protocol in workflow.md) - verified directly (prompting off):
      `sbt scalafmtCheck Test/scalafmtCheck test` in `tools/codegen/` green, 4/4 tests pass
      (well-formed spec, empty fields rejected, unknown type rejected, malformed YAML
      rejected).

## Phase 3: Field-insertion transformation logic
- [x] Task: Write failing tests (Red) - unit tests (small in-memory string fixtures, not real
      files) for the anchor-based line-insertion engine and each per-file-type field-renderer
      (case class field syntax, SQL column/type mapping for String/Int/Boolean/Instant, tuple
      (de)construction, test call-site argument insertion using `example` values); confirm they
      fail (no implementation yet). `8926e8f`
- [x] Task: Implement (Green) - the transformation engine + renderers. Run the suite, confirm
      green. `8926e8f`
- [x] Task: Conductor - User Manual Verification 'Phase 3: Field-insertion transformation logic'
      (Protocol in workflow.md) - verified directly (prompting off): `AnchorTransformer` and
      `FieldRenderers` cover all 27 anchor tags placed in Phase 1 (corrects that phase's note,
      which undercounted by one); 29/29 tests green, scalafmt clean.

## Phase 4: Wire transformations to real files + CLI entrypoint
- [x] Task: Write failing test (Red) - an integration-style munit test that runs the tool's
      `main()` against a checked-in fixture project (mirroring Phase 1's anchors) with a sample
      field-spec, asserting the resulting files match expected content; confirm it fails (no
      `Main` yet). `28e889c`

      Implemented as `CodegenToolSuite` against temp-dir fixtures built at test time (not
      checked-in files) so the test can assert on before/after content without mutating
      tracked fixtures.
- [x] Task: Implement (Green) - `Main.scala` (args: generated-project-dir, field-spec path),
      applying every transformation from Phase 3 across all anchor points. Run the suite, confirm
      green. `28e889c`

      `CodegenTool.runOn` is directory-agnostic about package/domain naming - it scans for any
      file containing a `codegen:fields:` marker rather than hardcoding paths, since those vary
      per generation.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Wire transformations to real files +
      CLI entrypoint' (Protocol in workflow.md) - verified directly (prompting off): 34/34 tests
      green, scalafmt clean; covers rewrite-in-place, target/-skip, missing-file/dir errors, and
      unknown-tag errors.

## Phase 5: End-to-end generation verification
- [x] Task: Write `scripts/verify-codegen-tool.sh` - generates a real `widget` service via
      giter8 (per the prior track), writes a sample field-spec (2+ fields, mixed types), runs
      the codegen tool against it, then runs `sbt scalafmtCheck test` on the result - this is the
      track's acceptance-criteria proof. `99c8029`
- [x] Task: Run the script; fix anything it surfaces; confirm green - new fields exercised
      through create/get/update/delete and the full CRUD lifecycle test. `99c8029`

      Green on the first run, no fixes needed: 9 files rewritten, no unresolved anchor
      markers, scalafmtCheck + 52/52 tests passed, all four sample fields
      (String/Int/Boolean/Instant) present on the generated `Widget` entity.
- [x] Task: Conductor - User Manual Verification 'Phase 5: End-to-end generation verification'
      (Protocol in workflow.md) - verified directly (prompting off) by running
      `./scripts/verify-codegen-tool.sh` against a real, freshly giter8-generated service; all
      six checks passed.

## Phase 6: Documentation
- [x] Task: Document the codegen tool in this repo's own README (field-spec YAML format, how to
      run it, the required-fields-only/no-idempotency limitations). `0052d23`
- [x] Task: Conductor - User Manual Verification 'Phase 6: Documentation' (Protocol in
      workflow.md) - verified directly (prompting off): new README section covers the field-spec
      format, how to run the tool, and its three limitations (additive-only/non-idempotent,
      uniform field visibility, required-only).

## Phase: Review Fixes
- [x] Task: Apply review suggestions `5e9160c`
