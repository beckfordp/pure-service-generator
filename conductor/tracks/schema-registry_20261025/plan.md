# Plan — Retrofit base template with schemaRegistryCheck (Rework 5 step 4, ADR 0003)

## Phase 1: Template changes + verification via dev-regenerate.sh scratch instance
- [ ] Task: Bump `src/main/g8/project/plugins.sbt`'s `sbt-purekafka-docs`
      `0.1.0-dev` -> `0.2.0-dev`; bump `src/main/g8/build.sbt`'s
      `purekafkaVersion` `0.2.1` -> `0.3.0`
- [ ] Task: Add `schemaRegistryUrl`/`schemaRegistryGroupId` settings to
      `src/main/g8/build.sbt` (same env-var-override shape as the 4
      existing services)
- [ ] Task: Add the same `docker run` Apicurio CI step shape as the 4
      existing services' `ci.yml` to
      `src/main/g8/.github/workflows/ci.yml`
- [ ] Task: Update `docs/examples.md`'s "AsyncAPI docs" section to cover
      `schemaRegistryCheck`
- [ ] Task: `./scripts/dev-regenerate.sh` (default domain `devcheck`),
      then run `sbt schemaRegistryCheck` in the scratch instance against
      the real local Apicurio instance - confirm the example channel
      registers + sets the `BACKWARD` rule; run the scratch instance's
      own full test suite - confirm no regressions
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Template
      changes + verification via dev-regenerate.sh scratch instance'
      (Protocol in workflow.md)
