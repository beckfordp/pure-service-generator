# Plan — Template: REST docs convention (gluon TD-20.2)

## Phase 1: purerestlib 0.7.3 [checkpoint: 98b073a]
- [x] Task: bump template `purerestlibVersion` 0.4.0 → 0.7.3 (+ any aligned tapir/purekafka pins); regenerate devcheck-service, fix API drift until its suite is green — no drift, 89/89 98b073a
- [x] Task: Conductor - User Manual Verification 'Phase 1: purerestlib 0.7.3' (Protocol in workflow.md) — covered by the regenerated suite

## Phase 2: Codegen `description`
- [ ] Task: tests first (FieldSpecParserSuite: `description` required, missing → error; FieldRenderersSuite: annotated cells per type incl. Instant-as-ISO-string; CodegenToolSuite fixtures) → add `description` to `Field`/parser, annotate CASE_CLASS_FIELD/CREATE_PARAMS/UPDATE_PARAMS; codegen README
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Codegen description' (Protocol in workflow.md)

## Phase 3: Template docs
- [ ] Task: coverage test first in `WidgetDocsSuite` (`Docs.undocumented(all endpoints) == Nil`, red) → approved summaries/descriptions/tags, parameter descriptions/examples, DTO + error annotations/examples in `WidgetRoutes`/`HealthRoutes`; class-level example for empty requests if needed; template README "Documenting endpoints" pointer
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Template docs' (Protocol in workflow.md)

## Phase 4: Smoke test
- [ ] Task: regenerate devcheck-service, suite green + zero gaps; run codegen with a sample field-spec (descriptions), suite green + zero gaps; `/docs/docs.yaml` shows summaries/descriptions/examples; scalafmtCheck
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Smoke test' (Protocol in workflow.md)
