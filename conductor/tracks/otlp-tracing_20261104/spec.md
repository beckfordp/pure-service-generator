# Spec: Template OTLP trace export (gluon PERF-5.3)

## Overview
The 8 gluon services moved from `Tracing.console` to `Tracing.resource`
with `TracingConfig.fromEnv(sys.env)` (purerest 0.10.0) so spans reach
Tempo. The template still generates `Tracing.console` on purerest 0.7.3.

## Functional Requirements
1. Template `purerestlibVersion` 0.7.3 → 0.10.0.
2. Generated `Telemetry.scala` matches the services: `tracingConfig[F]` from
   `TracingConfig.fromEnv(sys.env)` (malformed ratio fails startup) feeding
   `Tracing.resource`.

## Acceptance Criteria
- No `Tracing.console` in `src/main/g8/src/main`.
- A freshly generated service passes `sbt scalafmtCheck test`.

## Out of Scope
- Generated Helm values/env: gluon's `environments/local/` is hand-written per service.
