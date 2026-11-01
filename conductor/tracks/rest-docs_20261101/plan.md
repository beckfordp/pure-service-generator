# Plan — Template: REST docs convention (gluon TD-20.2)

## Phase 1: purerestlib 0.7.3 [checkpoint: 98b073a]
- [x] Task: bump template `purerestlibVersion` 0.4.0 → 0.7.3 (+ any aligned tapir/purekafka pins); regenerate devcheck-service, fix API drift until its suite is green — no drift, 89/89 98b073a
- [x] Task: Conductor - User Manual Verification 'Phase 1: purerestlib 0.7.3' (Protocol in workflow.md) — covered by the regenerated suite

## Phase 2: Codegen `description` [checkpoint: f8dcb90]
- [x] Task: tests first (FieldSpecParserSuite: `description` required, missing → error; FieldRenderersSuite: annotated cells per type incl. Instant-as-ISO-string; CodegenToolSuite fixtures) → add `description` to `Field`/parser, annotate CASE_CLASS_FIELD/CREATE_PARAMS/UPDATE_PARAMS; codegen README — deviation: those anchors also feed the entity/store, so new DTO-only anchors DTO_CREATE_FIELD/DTO_UPDATE_FIELD/DTO_RESPONSE_FIELD carry the annotations; 64/64 f8dcb90
- [x] Task: Conductor - User Manual Verification 'Phase 2: Codegen description' (Protocol in workflow.md) — devcheck + field-spec compiles; 2 pre-existing OutboxExampleSuite failures (example inserts only id)

## Phase 3: Template docs [checkpoint: 75e251e]
- [x] Task: coverage test first in `WidgetDocsSuite` (`Docs.undocumented(all endpoints) == Nil`, red) → approved summaries/descriptions/tags, parameter descriptions/examples, DTO + error annotations/examples in `WidgetRoutes`/`HealthRoutes`; class-level example for empty requests if needed; template README "Documenting endpoints" pointer — red 36 gaps → green; request body examples via TEST_*_REQUEST_ARGS anchors 8b980ad
- [x] Task: Outbox example codegen-aware — pre-existing: `OutboxExampleSuite` inserts only `id`, so NOT NULL codegen fields fail it; new `SQL_INSERT_EXAMPLE_ARGS` tag (example literals, all fields) + insert anchors (user decision 2026-11-01) 75e251e
- [x] Task: Conductor - User Manual Verification 'Phase 3: Template docs' (Protocol in workflow.md) — covered by Phase 4's verify script

## Phase 4: Smoke test [checkpoint: c8c29bb]
- [x] Task: regenerate devcheck-service, suite green + zero gaps; run codegen with a sample field-spec (descriptions), suite green + zero gaps; `/docs/docs.yaml` shows summaries/descriptions/examples; scalafmtCheck — bare 90/90, field-spec 90/90, `scripts/verify-rest-docs.sh` PASS c8c29bb
- [x] Task: Conductor - User Manual Verification 'Phase 4: Smoke test' (Protocol in workflow.md) — `scripts/verify-rest-docs.sh` PASS
