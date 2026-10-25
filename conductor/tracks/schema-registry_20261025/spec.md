# Spec — Retrofit base template with schemaRegistryCheck (Rework 5 step 4, ADR 0003)

## Overview
Rework 5 steps 1-3 (Apicurio Registry infra, `sbt-purekafka-docs`'s
`schemaRegistryCheck` task, and adoption in all 4 existing Kafka-touching
services) are all done. This step closes the loop for future services:
retrofit the g8 base template (`src/main/g8`) so every newly-generated
service gets the same wiring by default — mirroring Rework 3's own
generator-retrofit precedent, and the template's existing AsyncAPI-docs
wiring exactly (same settings shape, same example-channel convention).

## Functional Requirements
- Bump `src/main/g8/project/plugins.sbt`'s `sbt-purekafka-docs` version
  `0.1.0-dev` -> `0.2.0-dev`.
- Bump `src/main/g8/build.sbt`'s `purekafkaVersion` `0.2.1` -> `0.3.0`.
- Add `schemaRegistryUrl`/`schemaRegistryGroupId` settings to
  `src/main/g8/build.sbt` (same env-var-override shape as the 4 existing
  services), next to the existing `asyncApiChannelsClass` settings.
- Add the same `docker run` Apicurio CI step shape as the 4 existing
  services' `ci.yml` to `src/main/g8/.github/workflows/ci.yml`.
- Update `docs/examples.md`'s "AsyncAPI docs" section: mention
  `schemaRegistryCheck` reuses the same `ExampleAsyncApiChannels`
  registration, and extend both the "Promote to production" and "Remove
  entirely" instructions to cover the new settings/task (removing
  AsyncAPI docs should also mean removing the schema-registry wiring,
  since both depend on the same registration).

## Non-Functional Requirements
- No new design questions — reuses the design already settled across the
  4 service-adoption tracks verbatim.
- Verified via this repo's own established template-testing convention
  (`scripts/dev-regenerate.sh`'s scratch-instance flow, `.dev/`), not a
  new permanent verify script — consistent with how the AsyncAPI docs
  feature itself, and Rework 6's CORS fix, were verified.

## Acceptance Criteria
- A freshly generated scratch service (`./scripts/dev-regenerate.sh`,
  default domain `devcheck`) runs `sbt schemaRegistryCheck` successfully
  against the real local Apicurio instance, registering the example
  channel.
- The generated scratch service's own full test suite passes, no
  regressions from the template changes.
- `docs/examples.md` accurately reflects the new wiring.

## Out of Scope
- Any change to the generator's codegen tool (`tools/codegen`) or
  field-spec mechanism.
- Real GitHub Actions verification (the template's CI only runs for
  services actually generated from it — not testable as a GitHub Actions
  run from this repo itself).
