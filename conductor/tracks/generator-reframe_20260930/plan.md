# Plan: Remove order-service, reframe README around the generator, add a template dev workflow

## Phase 1: Remove order-service
Infrastructure/cleanup, not Red/Green — verified by regenerating the template and confirming
nothing else broke.
- [x] Task: Confirm `ClientResilienceExampleSuite` already exists in the g8 template
      (`src/main/g8/src/test/scala/$package$/examples/`), then delete
      `src/main/scala/orderservice/`, `src/test/scala/orderservice/`, root `build.sbt`,
      `project/`, `docker-compose.yml`, `.scalafmt.conf`. `639f0fc`

      Also removed order-service's own resource files that lived outside the `orderservice`
      package dirs (`src/main/resources/application.conf`,
      `src/main/resources/db/migration/V1__create_order_table.sql`,
      `src/test/resources/docker-java.properties`), which the original file list missed.
      Caught and immediately fixed a `rm -rf src/main src/test` slip that also deleted
      `src/main/g8/` (out of scope for this task) — restored via `git restore --source=HEAD`
      before anything was committed; confirmed the final diff touched only the intended files.
- [x] Task: Regenerate a `widget` service from the template and confirm it still compiles,
      scalafmt-checks, and passes its full test suite (52/52) — proves nothing in
      `src/main/g8/`/`tools/codegen/` depended on the removed root project. `639f0fc`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Remove order-service' (Protocol in
      workflow.md) - verified directly (prompting off): regenerated widget-service
      compiles/scalafmt-checks/52/52 tests pass; `tools/codegen`'s own suite still 60/60.

## Phase 2: Add scripts/dev-regenerate.sh and scripts/dev-diff.sh
- [x] Task: Write `scripts/dev-regenerate.sh` — `--domain-name` (default `devcheck`),
      `--package`, `--field-spec` (optional); generates into `.dev/<domain-name>-service`
      (wiped and regenerated fresh each run), optionally applies field-codegen, runs
      `sbt scalafmt test`, prints the resulting path. Also produces an untouched
      `.dev/<domain-name>-service.baseline` sibling copy from the same generation (before any
      `--field-spec` is applied to the editable copy). Add `.dev/` to `.gitignore`. `f66774e`
- [x] Task: Run it for real (with and without `--field-spec`); confirm a compiling, passing
      scratch instance each time, and that the `.baseline` sibling matches a fresh generation
      byte-for-byte. `f66774e`

      First live run surfaced a real bug: the `.baseline` copy was frozen *before* `sbt
      scalafmt` ran, so it differed from the editable copy purely from scalafmt's own
      reformatting (identifier-length line-wrapping), even with zero hand-edits. Fixed the
      ordering (field-spec → scalafmt → freeze baseline → test) before re-verifying; confirmed
      byte-for-byte identical (excluding `target/` build artifacts) on the corrected run.
- [x] Task: Write `scripts/dev-diff.sh` (plain `diff -ru` between the `.baseline` and editable
      copies, printed to the terminal — no auto-patching); make a real hand-edit to the scratch
      instance and confirm the diff shows exactly that edit, nothing more. `f66774e`
- [x] Task: Write `docs/developing-the-template.md` — the generate→edit→diff→port-back→
      regenerate workflow, and how to use `dev-regenerate.sh`/`dev-diff.sh`. `f66774e`
- [x] Task: Conductor - User Manual Verification 'Phase 2: Add scripts/dev-regenerate.sh and
      scripts/dev-diff.sh' (Protocol in workflow.md) - verified directly (prompting off) via
      the real runs above: zero-edit diff is empty, a real hand-edit shows exactly that edit.

## Phase 3: Split the docs
- [x] Task: Move the "Adding domain fields" section content into `tools/codegen/README.md`
      as-is (no rewrite needed, already accurate). `6b17472`
- [x] Task: Rewrite `README.md` — generator-first opening; "Generating a new service" promoted
      to the primary quickstart; links to `tools/codegen/README.md` and
      `docs/developing-the-template.md` in place of their former inline sections; reframe "One
      database per service"/"Calling other services with resilience"; remove the old
      order-service quickstart/testing sections. `6b17472`
- [x] Task: Conductor - User Manual Verification 'Phase 3: Split the docs' (Protocol in
      workflow.md) - verified directly (prompting off): no leftover order-service references in
      any of the three doc files; every relative link (README.md ↔ tools/codegen/README.md ↔
      docs/developing-the-template.md ↔ conductor/*.md ↔ the template's
      ClientResilienceExampleSuite.scala) resolves to a real file.

## Phase 4: Synchronize project documentation
- [x] Task: Update `product.md`'s Vision/Components/Key Features and `tech-stack.md`'s
      references to order-service, reflecting its removal (historical Iteration records stay
      unedited). Per `product-guidelines.md`'s own WARNING (strategic-shift-only edits), propose
      any changes there explicitly rather than editing silently — standard end-of-track doc-sync
      protocol (`AskUserQuestion` approval for each file). `819f847`

      `product-guidelines.md` reviewed and left untouched — its "order" naming-convention
      examples are illustrative, not claims about repo state, and nothing in this track rises
      to its own "significant strategic shift" bar.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Synchronize project documentation'
      (Protocol in workflow.md) - verified directly (prompting off): both files reviewed
      end-to-end for stray order-service framing after the edits; none remain outside the
      dated, intentionally-unedited historical Iteration/deviation records.
