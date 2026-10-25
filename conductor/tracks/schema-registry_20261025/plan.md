# Plan — Retrofit base template with schemaRegistryCheck (Rework 5 step 4, ADR 0003)

## Phase 1: Template changes + verification via dev-regenerate.sh scratch instance [checkpoint: TBD]
- [x] Task: Bump `src/main/g8/project/plugins.sbt`'s `sbt-purekafka-docs`
      `0.1.0-dev` -> `0.2.0-dev`; bump `src/main/g8/build.sbt`'s
      `purekafkaVersion` `0.2.1` -> `0.3.0` `9188057`
- [x] Task: Add `schemaRegistryUrl`/`schemaRegistryGroupId` settings to
      `src/main/g8/build.sbt` (same env-var-override shape as the 4
      existing services) `9188057`
- [x] Task: Add the same `docker run` Apicurio CI step shape as the 4
      existing services' `ci.yml` to
      `src/main/g8/.github/workflows/ci.yml` `9188057` — confirmed via
      a real scratch-instance generation that g8's `$...$` substitution
      correctly left the new step's `$(seq 1 60)` shell syntax and
      `\${{ }}` GitHub Actions expressions intact
- [x] Task: Update `docs/examples.md`'s "AsyncAPI docs" section to cover
      `schemaRegistryCheck` `9188057`
- [x] Task: `./scripts/dev-regenerate.sh` (default domain `devcheck`),
      then run `sbt schemaRegistryCheck` in the scratch instance against
      the real local Apicurio instance - confirm the example channel
      registers + sets the `BACKWARD` rule; run the scratch instance's
      own full test suite - confirm no regressions — found and worked
      around along the way: the first `dev-regenerate.sh` run was
      interrupted mid-script, leaving a partial `.dev/devcheck-service`
      directory that blocked a clean `rm -rf` on the next run
      ("Directory not empty"); manually removed it (confirmed gitignored
      scratch output, not tracked) before retrying. Second run: 68/68
      tests passed; `schemaRegistryCheck` registered the example channel
      (`devcheck-events-ExampleEvent`) under the real `devcheck-service`
      group in the real local Apicurio instance
- [x] Task: Conductor - User Manual Verification 'Phase 1: Template
      changes + verification via dev-regenerate.sh scratch instance'
      (Protocol in workflow.md) — reduced-prompting mode: the real
      scratch-instance generation + test run + schemaRegistryCheck
      invocation is the verification; no interactive walkthrough
