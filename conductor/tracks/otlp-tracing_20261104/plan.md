# Plan: Template OTLP trace export (gluon PERF-5.3)

## Phase 1: Template
- [ ] Task: `src/main/g8/build.sbt` purerestlib 0.7.3 → 0.10.0
- [ ] Task: `Telemetry.scala` template: `Tracing.console` → `Tracing.resource` via `tracingConfig`
- [ ] Task: Regenerate (`scripts/dev-regenerate.sh`) and `sbt scalafmtCheck test` the generated service
- [ ] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md)
