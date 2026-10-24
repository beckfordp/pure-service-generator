# Plan — purekafka examples + AsyncAPI docs for the base template

## Phase 1: Dependency pins + always-on AsyncAPI channel registration [checkpoint: c3f7271]
- [x] Task 1.1: Add temporary local-snapshot dependency pins (purekafka, fs2-kafka,
      sbt-purekafka-docs) to `build.sbt`/`project/plugins.sbt`, mirroring order-service's own
      pin comments (re-pin once GitHub Packages release lands) `52e8b3b`
- [x] Task 1.2: Create `$package$/examples/ExampleAsyncApiChannels.scala` (an
      `AsyncApiChannelsProvider` registering one illustrative `"$domain_name$-events"` channel
      + generic `ExampleEvent` payload, with an enum-validated `status` field) `81e2184`,
      amended `02e46cb` (moved into `examples/` subpackage alongside
      `ClientResilienceExampleSuite`, test renamed to `AsyncApiChannelsExampleSuite` per its
      naming convention, added the `status` enum field)
  - [x] Red: `AsyncApiChannelsExampleSuite.scala` asserting the provider's `channels` list
        contains the expected topic/eventType
  - [x] Green: implement `ExampleAsyncApiChannels`
- [x] Task 1.3: Wire `PureKafkaDocsPlugin` into `build.sbt` (`enablePlugins`,
      `asyncApiChannelsClass`/`asyncApiTitle`/`asyncApiVersion`) `4fe46de`
- [x] Task 1.4: Serve the raw AsyncAPI spec from `Main`'s routes (`/docs/asyncapi.json` via
      `purekafka.docs.Docs.routes`, matching order-service) `8603764`
  - [x] Red/Green: no unit test for `Main` exists in this template (same as order-service);
        verified live instead — curled `/docs/asyncapi.json` against a running generated
        service, byte-for-byte identical to Task 1.3's build-time output, enum included
- [x] Task: Conductor - User Manual Verification 'Phase 1: Dependency pins + AsyncAPI wiring'
      (Protocol in workflow.md) `c3f7271`

## Phase 2: Producer resilience example [checkpoint: d5c5a7d]
- [x] Task 2.1: Add `example-kafka-producer` config block to `application.conf` (same "not read
      by the live service" convention as `example-client`)
- [x] Task 2.2: `KafkaProducerResilienceExampleSuite.scala`
  - [x] Red: tests against a fake publish function — transient-failure-then-success (retried,
        succeeds), and sustained failure (circuit breaker opens)
  - [x] Green: implement the example composition (`Resilience.middleware` + PureConfig-loaded
        `ResilienceConfig`)
- [x] Task: Conductor - User Manual Verification 'Phase 2: Producer resilience example'
      (Protocol in workflow.md) `d5c5a7d`

## Phase 3: Consumer supervision + DLQ example [checkpoint: 46cb41f]
- [x] Task 3.1: `KafkaConsumerExampleSuite.scala`
  - [x] Red: tests proving (a) a crashing synthetic `Stream` gets restarted via
        `StreamSupervisor`, (b) a permanently-failing message is routed to the DLQ via
        `DeadLetter` — mirroring purekafka's own `StreamSupervisorSuite`/`DeadLetterSuite`
        synthetic-stream convention, no Docker Kafka
  - [x] Green: implement the example
- [x] Task: Conductor - User Manual Verification 'Phase 3: Consumer supervision + DLQ example'
      (Protocol in workflow.md) `46cb41f`

## Phase 4: Outbox-CDC example [checkpoint: 94c817e]
- [x] Task 4.1: `V2__create_outbox_events.sql` migration (copy order-service's generic schema:
      id/aggregate_id/payload/trace_parent/correlation_id/created_at)
  - [x] Red: a migrations test asserting the `outbox_events` table exists post-migration,
        alongside the existing `V1` table
  - [x] Green: add the migration
- [x] Task 4.2: `OutboxExampleSuite.scala` — a self-contained, Testcontainers-Postgres-backed
      example demonstrating a same-transaction insert into both the base entity's table and
      `outbox_events` (does not touch the real `$domain_name$Store`)
  - [x] Red: write the test
  - [x] Green: implement the example
- [x] Task: Conductor - User Manual Verification 'Phase 4: Outbox-CDC example' (Protocol in
      workflow.md) `94c817e`

## Phase 5: Documentation + final verification [checkpoint: 5b37baf]
- [x] Task 5.1: `docs/examples.md` — all four examples (HTTP `ClientResilienceExampleSuite` +
      the three new Kafka ones): what each demonstrates, how to promote to production code, how
      to remove cleanly (flagging the outbox migration's append-only caveat)
- [x] Task 5.2: Short pointer section in `README.md` linking to `docs/examples.md`, same style
      as the "Developing the template" section
- [x] Task 5.3: `./scripts/dev-regenerate.sh --domain-name devcheck` → confirm `sbt scalafmt
      test` green (including all new example suites) → confirm `sbt compile` produces a real,
      non-empty `target/asyncapi-docs-spec/asyncapi.json` + `target/asyncapi-docs/index.html`
- [x] Task: Conductor - User Manual Verification 'Phase 5: Documentation + final verification'
      (Protocol in workflow.md) `5b37baf`

## Phase: Review Fixes
- [x] Task: Apply review suggestions `e1e43fc`
