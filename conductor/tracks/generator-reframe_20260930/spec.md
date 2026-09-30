# Spec: Remove order-service, reframe README around the generator, add a template dev workflow

## Overview
`order-service` at the repo root has diverged from what it's meant to demonstrate (the
`base-fields-spec` track stripped the *template's* base entity to `id`/`createdAt`/`updatedAt`;
`order-service` still hardcodes `item`/`quantity`/`status`) and duplicates what the generator can
now produce on demand. This track removes it, reframes the README to center the generator (not
a specific service), and adds a `scripts/dev-regenerate.sh` helper so template development still
has something real and compiler-checked to iterate against.

## Functional Requirements
1. **Remove `order-service`**: `src/main/scala/orderservice/`, `src/test/scala/orderservice/`,
   root `build.sbt`, `project/`, `docker-compose.yml`, `.scalafmt.conf` — nothing else in the
   repo depends on the root sbt project (`tools/codegen/` and the template's own `project/` are
   independent). Confirm `ClientResilienceExampleSuite` already exists in the g8 template
   (`src/main/g8/src/test/scala/$package$/examples/`) before removing order-service's copy, so
   the resilience-pattern documentation stays accurate for generated services.
2. **`scripts/dev-regenerate.sh`**: generates a scratch instance from the template (reusing the
   `giter8.LauncherMain` pattern) into a fixed, gitignored local directory
   (`.dev/<domain-name>-service`, wiped and regenerated fresh each run), optionally applies a
   `--field-spec` via `tools/codegen`, then runs `sbt scalafmt test` and prints the path — one
   command to get something real to develop against. Flags mirror
   `generate-and-publish-service.sh`'s style: `--domain-name` (default `devcheck`), `--package`,
   `--field-spec`.
3. **README restructure**: lead with "this is a generator"; "Generating a new service" becomes
   the primary quickstart (promoted above the old order-service quickstart, which is removed);
   add a "Developing the template" section documenting the generate→edit→port-back→regenerate
   workflow and `dev-regenerate.sh`; reframe "One database per service" and "Calling other
   services with resilience" around what the generator/template produces rather than
   order-service specifically.
4. **Doc sync** (`conductor/product.md`, `tech-stack.md`, and, given the WARNING in
   `product-guidelines.md` about strategic-shift-only edits, a careful pass there too): update
   current-state sections (Vision, Components, Key Features) to reflect order-service's removal
   — historical Iteration records stay as dated, unedited log entries.

## Non-Functional Requirements
- `.dev/` added to `.gitignore` so `dev-regenerate.sh` output never gets committed.
- No change to `src/main/g8/` or `tools/codegen/` content/behavior — this track only removes
  the root reference service and adds tooling/docs.

## Acceptance Criteria
- `sbt compile`/`sbt test` no longer resolve at the repo root (no root sbt project); `tools/codegen`
  and the template still work exactly as before (verified via the same live-verification pattern
  used throughout this project).
- `scripts/dev-regenerate.sh` run for real produces a compiling, passing scratch instance.
- README no longer mentions `order-service` as this repo's subject; leads with the generator.

## Out of Scope
- A `src/test/g8/` giter8 "scripted" test (the canonical giter8 CI-integrated alternative) —
  `dev-regenerate.sh` covers the immediate workflow need; formal CI-integrated template testing
  can be a future track if wanted.
- Any change to the field-codegen tool, the g8 template's own content, or the publish pipeline.
