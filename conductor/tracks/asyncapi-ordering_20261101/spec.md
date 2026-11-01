# Spec — Template: AsyncAPI sends first (purekafka 0.6.1) (gluon TD-19.6)

## Overview
purekafka v0.6.1 lists every AsyncAPI `send` operation before every `receive`, registration order within each group, so a service's own events open its docs. The six Kafka services moved to it (TD-19.6); the template is on `0.6.0`.

## Functional Requirements
1. **Bump:** purekafka `0.6.0` → `0.6.1`; sbt-purekafka-docs `0.6.0-dev` → `0.6.1-dev`; pin comments updated.
2. **Docs:** `docs/examples.md` notes sends are listed before receives.

## Acceptance Criteria
- A freshly generated scratch service (`scripts/dev-regenerate.sh`) compiles, its tests pass, and its generated spec is 3.0.0 and passes `asyncapi validate`

## Out of Scope
- Example channel changes (the template registers one consumed event, so order is unaffected)
