# Plan: Template OTLP trace export (gluon PERF-5.3)

## Phase 1: Template
- [x] Task: `src/main/g8/build.sbt` purerestlib 0.7.3 → 0.10.0 e13ff5d
- [x] Task: `Telemetry.scala` template: `Tracing.console` → `Tracing.resource` via `tracingConfig` e13ff5d
- [x] Task: Regenerate (`scripts/dev-regenerate.sh`) and `sbt scalafmtCheck test` the generated service (otlpcheck: 90 tests, green)
- [x] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md)

## Review
- No findings: same change as the 8 services' PERF-5.3 tracks (reviewed there).
