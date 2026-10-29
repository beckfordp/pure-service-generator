# TD-15 generator backport — Implementation Plan

## Phase 1 — Template changes
- [ ] Add `domain/<Entity>Id` template; type store/routes; asyncApiHtmlDir config; ScalaCheck dep/property; narrow bounds.
- [ ] Update generated tests for typed ids/malformed 400.
- [ ] Task: Conductor - User Manual Verification 'Phase 1 — Template' (Protocol in workflow.md)

## Phase 2 — Fresh-generation smoke test
- [ ] Generate a scratch service via the dev regeneration script; compile + test; check AsyncAPI dir config.
- [ ] Task: Conductor - User Manual Verification 'Phase 2 — Smoke test' (Protocol in workflow.md)
