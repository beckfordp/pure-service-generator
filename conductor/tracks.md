# Project Tracks

This file tracks all major tracks for the project.

- [~] **Track: Add a --private flag to the publish pipeline (scripts/generate-and-publish-service.sh)**
  *Link: [./tracks/private-repo-flag_20260930/](./tracks/private-repo-flag_20260930/)*

---

## Backlog

Title-only placeholders for future tracks — not yet detailed (no spec/plan, no linked
folder), so `/conductor:implement` cannot pick these up by accident. Reorder freely as
priorities change. When ready to work on one, run `/conductor:newTrack <title>` to go
through the spec/plan questions and promote it into a real track below.

- Add generated `purekafka` examples to the base template
  (`src/main/g8`), mirroring the existing
  `src/test/scala/$package$/examples/ClientResilienceExampleSuite.scala`
  pattern for the one resilience primitive the template already knows
  about (HTTP, via `purerestlib`'s `Resilience.middleware`). Three new
  examples, each with its own test: a producer example (publish through
  `purekafka`'s generic `F[A] => F[A]` resilience wrapper), a consumer
  example (a supervised `Stream[F, O]` with DLQ routing), and an
  outbox-CDC producer example (the ADR 0008 shape — write an envelope
  row to an `outbox_events` table inside the same transaction as a
  state change, no direct Kafka call at all). Without this, every
  Kafka-touching generated service re-derives the wiring from
  `purekafka`'s own docs instead of copying a proven, tested example —
  the exact gap that let gluon's TD-2 happen in the first place.
  **Sequenced after `purekafka` is published** (`purerest`'s own
  backlog) — the examples have to compile against a real artifact, not
  a planned one. Decided in gluon's
  [ADR 0010](https://github.com/beckfordp/gluon/blob/main/docs/adr/0010-purekafka-module-for-kafka-resilience-observability.md)
  — full context there.

---
