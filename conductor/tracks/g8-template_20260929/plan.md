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

## Phase 2: Template the Scala source
- [ ] Task: Copy `src/main/scala/orderservice/*.scala` into
      `src/main/g8/src/main/scala/$package_name$/` (directory driven by `package_name`, not
      `domain_name`), with files renamed and contents substituted: `package orderservice` →
      `package $package_name$`; `Order` → `$domain_name;format="Cap"$` (class/identifier names);
      `/orders` → `/$domain_name$s`; `order-service` → `$domain_name$-service`, etc.
- [ ] Task: Same treatment for `src/test/scala/orderservice/*.scala` (including
      `examples/ClientResilienceExampleSuite`, repackaged to `$package_name$.examples` only — no
      domain-specific identifiers) and `src/test/resources/docker-java.properties` (copied through
      unchanged).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Template the Scala source' (Protocol in
      workflow.md)

## Phase 3: Template the generated README; document the template in this repo's own README
- [ ] Task: Write `src/main/g8/README.md` (templated — documents the *generated* service,
      mirroring this repo's current README with `$domain_name$`/`$package_name$` substitutions).
- [ ] Task: Add a "Generating a new service from this template" section to this repo's own
      top-level `README.md`: prerequisites, the `sbt new file://<path>` command, both properties
      (`domain_name`, `package_name`) and their defaults, and the naive-pluralization caveat.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Template the generated README; document
      the template in this repo's own README' (Protocol in workflow.md)

## Phase 4: End-to-end generation verification
- [ ] Task: Write `scripts/verify-g8-template.sh` — runs `sbt new file://<repo-path>
      --domain_name=widget` (default `package_name`) into a temp directory, runs
      `sbt scalafmtCheck test` inside the generated project, greps the generated output for
      leftover `order`/`Order`/`orderservice` references (expect none), confirms this repo's own
      reference service still builds/tests standalone, then deletes the generated temp directory.
- [ ] Task: Run the script; fix anything it surfaces; confirm green — this is the track's
      acceptance-criteria proof. Also spot-check overriding `package_name` independently (e.g.
      `--package_name=com.example.widgetservice`) generates and compiles correctly.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: End-to-end generation verification'
      (Protocol in workflow.md)
