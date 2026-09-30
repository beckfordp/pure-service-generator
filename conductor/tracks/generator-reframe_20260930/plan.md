# Plan: Remove order-service, reframe README around the generator, add a template dev workflow

## Phase 1: Remove order-service
Infrastructure/cleanup, not Red/Green — verified by regenerating the template and confirming
nothing else broke.
- [ ] Task: Confirm `ClientResilienceExampleSuite` already exists in the g8 template
      (`src/main/g8/src/test/scala/$package$/examples/`), then delete
      `src/main/scala/orderservice/`, `src/test/scala/orderservice/`, root `build.sbt`,
      `project/`, `docker-compose.yml`, `.scalafmt.conf`.
- [ ] Task: Regenerate a `widget` service from the template and confirm it still compiles,
      scalafmt-checks, and passes its full test suite (52/52) — proves nothing in
      `src/main/g8/`/`tools/codegen/` depended on the removed root project.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Remove order-service' (Protocol in
      workflow.md)

## Phase 2: Add scripts/dev-regenerate.sh and scripts/dev-diff.sh
- [ ] Task: Write `scripts/dev-regenerate.sh` — `--domain-name` (default `devcheck`),
      `--package`, `--field-spec` (optional); generates into `.dev/<domain-name>-service`
      (wiped and regenerated fresh each run), optionally applies field-codegen, runs
      `sbt scalafmt test`, prints the resulting path. Also produces an untouched
      `.dev/<domain-name>-service.baseline` sibling copy from the same generation (before any
      `--field-spec` is applied to the editable copy). Add `.dev/` to `.gitignore`.
- [ ] Task: Run it for real (with and without `--field-spec`); confirm a compiling, passing
      scratch instance each time, and that the `.baseline` sibling matches a fresh generation
      byte-for-byte.
- [ ] Task: Write `scripts/dev-diff.sh` (plain `diff -ru` between the `.baseline` and editable
      copies, printed to the terminal — no auto-patching); make a real hand-edit to the scratch
      instance and confirm the diff shows exactly that edit, nothing more.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Add scripts/dev-regenerate.sh and
      scripts/dev-diff.sh' (Protocol in workflow.md)

## Phase 3: README restructure
- [ ] Task: Rewrite the README — generator-first opening; "Generating a new service" promoted
      to the primary quickstart; new "Developing the template" section (the
      generate→edit→port-back→regenerate workflow, `dev-regenerate.sh`); reframe "One database
      per service" and "Calling other services with resilience" around the generator/template
      rather than order-service; remove the old order-service quickstart/testing sections.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: README restructure' (Protocol in
      workflow.md)

## Phase 4: Synchronize project documentation
- [ ] Task: Update `product.md`'s Vision/Components/Key Features and `tech-stack.md`'s
      references to order-service, reflecting its removal (historical Iteration records stay
      unedited). Per `product-guidelines.md`'s own WARNING (strategic-shift-only edits), propose
      any changes there explicitly rather than editing silently — standard end-of-track doc-sync
      protocol (`AskUserQuestion` approval for each file).
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Synchronize project documentation'
      (Protocol in workflow.md)
