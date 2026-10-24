# Spec — purekafka examples + AsyncAPI docs for the base template

## Overview
Retrofit pure-service-generator's base template (`src/main/g8`) with three new generated
`purekafka` examples — mirroring the existing `ClientResilienceExampleSuite.scala` pattern for
the template's one other resilience primitive (HTTP, via purerestlib's `Resilience.middleware`)
— plus always-on AsyncAPI documentation (`sbt-purekafka-docs`). This is gluon PLAN.md's Rework 3
(ADR 0010) generator-retrofit item, merged with pure-service-generator's own pre-existing backlog
entry for the three examples. Builds against purekafka/sbt-purekafka-docs' current local-only
publish (GitHub Packages release blocked on a billing issue) — same temporary pin convention
order-service already uses, with a comment flagging it for re-pinning later.

## Functional Requirements
1. **Producer resilience example** — `src/test/scala/$package$/examples/KafkaProducerResilienceExampleSuite.scala`:
   wraps a fake publish call with `purekafka.resilience.Resilience.middleware` (retry+circuit-breaker),
   config loaded via PureConfig from an `example-kafka-producer` section in `application.conf`
   (same "not read by the live service" convention `example-client` already uses). No real Kafka
   broker — a synthetic `F[Unit]` publish function, same as `ClientResilienceExampleSuite`'s dummy
   client.
2. **Consumer supervision + DLQ example** — `src/test/scala/$package$/examples/KafkaConsumerExampleSuite.scala`:
   a `Stream[F, O]` wrapped in `purekafka.consumer.StreamSupervisor` (restart-with-backoff) with
   `purekafka.consumer.DeadLetter` routing (including the `dlq-publish-resilience` guard added to
   `DeadLetter` this session). Synthetic `fs2.Stream`, not a real `KafkaConsumer.stream` — no
   Docker Kafka needed, same convention purekafka's own `StreamSupervisorSuite`/`DeadLetterSuite`
   use.
3. **AsyncAPI channel registration (always on, in `src/main`)** — a small
   `$package$/ExampleAsyncApiChannels.scala` (an `AsyncApiChannelsProvider` singleton)
   registering one illustrative channel (`"$domain_name$-events"` topic, a generic `ExampleEvent`
   payload) — this is what makes `sbt-purekafka-docs`'s build-time generation produce real (if
   illustrative) output instead of an empty doc, and is also wired into `Main`'s routes so
   `/docs/asyncapi.json` serves it, matching order-service's existing pattern.
4. **sbt-purekafka-docs wiring (always on)** — `PureKafkaDocsPlugin` enabled in the template's
   `build.sbt` (`asyncApiChannelsClass` pointing at `ExampleAsyncApiChannels`,
   `asyncApiTitle`/`asyncApiVersion` defaulted), plugin added to `project/plugins.sbt`. Generates
   `target/asyncapi-docs-spec/asyncapi.json` + `target/asyncapi-docs/index.html` on every `sbt
   compile`, same as order-service.
5. **Outbox-CDC example** — a real migration,
   `src/main/g8/src/main/resources/db/migration/V2__create_outbox_events.sql`, copying
   order-service's actual `outbox_events` schema (id/aggregate_id/payload/trace_parent/correlation_id/created_at
   — already fully generic, no domain-specific columns). A self-contained example (test-space,
   like the other two) demonstrating a same-transaction insert into both the base entity's table
   and `outbox_events`, tested against a real Testcontainers-Postgres (the template's existing
   DB-test convention) — does *not* modify the real `$domain_name$Store`/migrations/routes, same
   "illustrative, zero production wiring" stance as the other two examples.
6. **Dependency pin** — `build.sbt`/`project/plugins.sbt` add `purekafka`/`fs2-kafka`/`sbt-purekafka-docs`
   at their current local-snapshot versions (mirroring order-service's own
   `purekafkaVersion`/plugin pins and comments), to be re-pinned once the real GitHub Packages
   release lands.
7. **Examples guide** — a new `docs/examples.md` (linked from `README.md`, same pattern as the
   existing `docs/writing-main.md` link) covering **all four** generated examples — the existing
   HTTP `ClientResilienceExampleSuite` plus the three new Kafka ones. For each: what it
   demonstrates, **how to promote it to production code** (which files to copy/adapt, which
   `example-*` config block to rename/move into the real service config), and **how to remove it
   cleanly if unused**, explicitly listing what's safe to delete (the `ExampleAsyncApiChannels`
   registration + its build.sbt/plugins.sbt wiring if AsyncAPI docs aren't wanted, the
   `example-*` config blocks, the example test files themselves) and flagging the one exception:
   the `outbox_events` migration shouldn't be deleted after it's already been applied in any
   real/deployed environment — Flyway migrations are append-only history, not a toggle.
   `README.md` gets a short pointer section to this doc, same style as its "Developing the
   template" section pointing at `docs/developing-the-template.md`.

## Non-Functional Requirements
- No Docker Kafka dependency added to a generated service's default test run (per the
  synthetic-test decision) — only Postgres (already required), and the outbox example adds
  nothing new either (same Postgres).
- Every generated service pulls in `fs2-kafka`/`purekafka` as compile-scope dependencies
  regardless of whether it ever touches Kafka for real — accepted cost of the "examples, not
  generator flags" decision made earlier for the httpclient precedent.
- Verified via the template's own `docs/developing-the-template.md` workflow (`dev-regenerate.sh`
  → edit/test the scratch copy → `dev-diff.sh` → port back → regenerate+confirm).

## Acceptance Criteria
- `./scripts/dev-regenerate.sh --domain-name devcheck` produces a service where `sbt scalafmt
  test` is green, including the 2 new example suites and the new outbox-CDC example's test.
- `sbt compile` on the generated service produces a real (non-empty)
  `target/asyncapi-docs-spec/asyncapi.json` and `target/asyncapi-docs/index.html`, with the
  illustrative channel present.
- The new `V2__create_outbox_events.sql` migration applies cleanly alongside the existing
  `V1__create_$domain_name$_table.sql`.
- No changes to the real `$domain_name$Store`/`Main.scala`/`Routes.scala` beyond the AsyncAPI
  channel wiring in (3)/(4) above.

## Out of Scope
- Wiring any example into the generated service's actual `Main.scala`/production code path
  (beyond the AsyncAPI channel registration) — these stay illustrative, like
  `ClientResilienceExampleSuite`.
- A messaging field-spec (declaring topics/events per-domain) — PLAN.md's own "longer-term, not
  this ADR's scope" item, not this track.
- Adoption across inventory-service/payment-service/notification-service — separate PLAN.md
  bullets, not this track.
- Re-pinning to the real published purekafka/sbt-purekafka-docs versions — follow-up once GitHub
  Packages billing is resolved.
