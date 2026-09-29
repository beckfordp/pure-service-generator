# Plan: Build a giter8 (g8) template for service/domain/package renaming

## Phase 1: Scaffold the template structure and non-Scala files [checkpoint: f0bb1ba]
- [x] Task: Create `src/main/g8/default.properties` (`domain_name = widget`,
      `package_name = $domain_name$service`). Copy `build.sbt`, `project/build.properties`,
      `project/plugins.sbt`, `.scalafmt.conf`, `docker-compose.yml`,
      `src/main/resources/application.conf`, and the Flyway migration SQL into `src/main/g8/`,
      substituting `$domain_name$` throughout (sbt project/Docker image name, Postgres
      db/user/password, migration table name). [60f52c0] Used property name `package` (not
      `package_name`) for giter8's built-in dot-to-slash directory-name conversion.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Scaffold the template structure and
      non-Scala files' (Protocol in workflow.md). Prompting off: verified via
      `scripts/verify-g8-phase1-scaffold.sh` instead of an interactive walkthrough. [f0bb1ba]

## Phase 2: Template the Scala source [checkpoint: ecee915]
- [x] Task: Copy `src/main/scala/orderservice/*.scala` into
      `src/main/g8/src/main/scala/$package$/` (directory driven by `package`, not `domain_name`
      — corrected from `package_name` per Phase 1's naming adjustment), with files renamed and
      contents substituted: `package orderservice` → `package $package$`; `Order` →
      `$domain_name;format="cap"$` (class/identifier names — `cap` not `Cap`, giter8's format
      names are lowercase); `/orders` → `/$domain_name$s`; `order-service` →
      `$domain_name$-service`, etc. All literal Scala/Skunk `$` interpolations escaped as `\$`.
      [ee5da7b]
- [x] Task: Same treatment for `src/test/scala/orderservice/*.scala` (including
      `examples/ClientResilienceExampleSuite`, repackaged to `$package$.examples` only — no
      domain-specific identifiers) and `src/test/resources/docker-java.properties` (copied through
      unchanged). [ee5da7b]
- [x] Task: Conductor - User Manual Verification 'Phase 2: Template the Scala source' (Protocol in
      workflow.md). Prompting off: generated a real "widget" service via `giter8.LauncherMain`
      (see Phase 4 note on why not `sbt new`) and ran `sbt scalafmt scalafmtCheck test` against
      it — 52/52 pass. [ee5da7b]

## Phase 3: Template the generated README; document the template in this repo's own README [checkpoint: 5dca445]
- [x] Task: Write `src/main/g8/README.md` (templated — documents the *generated* service,
      mirroring this repo's current README with `$domain_name$`/`$package$` substitutions).
      [3f15a4c] Verified end-to-end against a real generated "widget-service" (every
      documented curl command works).
- [x] Task: Add a "Generating a new service from this template" section to this repo's own
      top-level `README.md`: prerequisites, the generation command (see Phase 4 note — not
      `sbt new file://<path>`, which doesn't work in this environment/sbt version), both
      properties (`domain_name`, `package`) and their defaults, and the naive-pluralization
      caveat. [31ad74c] Verified the giter8-launcher fallback command works against this repo,
      pointed at the repo root; plain `sbt new file://` is documented as worth trying first
      (works in some sbt configurations) but was NOT independently verified to succeed here —
      it's the same command already confirmed broken in this environment/sbt version.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Template the generated README; document
      the template in this repo's own README' (Protocol in workflow.md). Prompting off: verified
      directly instead of an interactive walkthrough. [5dca445]

## Phase 4: End-to-end generation verification [checkpoint: f662fab]
**Note (discovered during Phase 2):** `sbt new file://<path>` does not work in this environment —
sbt's built-in `new` command only resolves a small hardcoded list of GitHub template shortcuts
(`scala/scala3.g8`, etc.), not arbitrary `file://` URIs, in this sbt version. Verification (and
the README's documented generation command) instead uses giter8's own `giter8-launcher` library
directly: `sbt runMain giter8.LauncherMain file://<path> --domain_name=... -o <output-dir>`, via
a project-local (not global) `libraryDependencies` addition in the consuming project — nothing
about the user's global sbt environment changes. A real end user without this constraint could
still try plain `sbt new file://<path>` first.
- [x] Task: Write `scripts/verify-g8-template.sh` — runs the generation command above with
      `--domain_name=widget` (default `package`) into a temp directory, runs
      `sbt scalafmtCheck test` inside the generated project, greps the generated output for
      leftover `order`/`Order`/`orderservice` references (expect none), confirms this repo's own
      reference service still builds/tests standalone, then deletes the generated temp directory.
      The underlying checks were already run manually during Phase 2 (to debug the template);
      this task formalizes them into a committed, re-runnable script. [fa16f64]
- [x] Task: Run the script; fix anything it surfaces; confirm green — this is the track's
      acceptance-criteria proof. Also spot-check overriding `package` independently (e.g.
      `--package=com.example.widgetservice`) generates and compiles correctly. [fa16f64] All 5
      checks passed on first run.
- [x] Task: Conductor - User Manual Verification 'Phase 4: End-to-end generation verification'
      (Protocol in workflow.md). Prompting off: verified via
      `scripts/verify-g8-template.sh` instead of an interactive walkthrough. [f662fab]
