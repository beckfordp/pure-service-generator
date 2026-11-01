# Spec — Template: AsyncAPI 3.0 docs, described example consumed event (gluon TD-19.5)

## Overview
purekafka v0.6.0 emits AsyncAPI 3.0 (`send`/`receive` from the service's own side) and redirects `/docs/asyncapi` → `/docs/asyncapi/` (gluon TD-19.1/19.2). The six Kafka services moved to it and gave their consumed events descriptions/examples copied from gluon's `registry/` (TD-19.3/19.4). The template is still on purekafka `0.3.0` / sbt-purekafka-docs `0.3.0-dev`, and its example consumed `ExampleEvent` has no descriptions.

## Functional Requirements
1. **Bump:** purekafka `0.3.0` → `0.6.0`; sbt-purekafka-docs `0.3.0-dev` → `0.6.0-dev` (also brings 0.4's Node >= 24.11 HTML and 0.5's producer-only registry).
2. **Example:** `ExampleEvent` gets `@description`/`@encodedExample` on the class and fields, with a comment pointing authors at the producer's registry artifact as the source to copy from.
3. **Tests:** `AsyncApiChannelsExampleSuite` checks the 3.0 `receive` operation and the description/examples.
4. **Docs:** template README and `docs/examples.md` describe 3.0 send/receive, the redirect, and copying consumed descriptions from the registry.

## Acceptance Criteria
- A freshly generated scratch service (`scripts/dev-regenerate.sh`) compiles, its tests pass, and its generated spec is 3.0.0 and passes `asyncapi validate`

## Out of Scope
- Template CI Node setup and registry-baseline restore (TD-18.5 shape) for generated services
