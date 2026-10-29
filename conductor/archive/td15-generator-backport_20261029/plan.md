# TD-15 generator backport — Implementation Plan

## Phase 1 — Template changes
- [x] Add `domain/<Entity>Id` template; type store/routes; asyncApiHtmlDir config; ScalaCheck dep/property; narrow bounds. — 16b6889
- [x] Update generated tests for typed ids/malformed 400. — 16b6889
- [x] Task: Conductor - User Manual Verification 'Phase 1 — Template' (Protocol in workflow.md) — covered by the generated suite

## Phase 2 — Fresh-generation smoke test
- [x] Generate a scratch service via the dev regeneration script; compile + test; check AsyncAPI dir config. — devcheck-service: 88/88 tests green
- [x] Task: Conductor - User Manual Verification 'Phase 2 — Smoke test' (Protocol in workflow.md) — covered by the generated suite
