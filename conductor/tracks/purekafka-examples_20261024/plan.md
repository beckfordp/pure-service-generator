# Plan — purekafka examples + AsyncAPI docs for the base template

## Phase 1: Dependency pins + always-on AsyncAPI channel registration
- [ ] Task 1.1: Add temporary local-snapshot dependency pins (purekafka, fs2-kafka,
      sbt-purekafka-docs) to `build.sbt`/`project/plugins.sbt`, mirroring order-service's own
      pin comments (re-pin once GitHub Packages release lands)
- [ ] Task 1.2: Create `$package$/ExampleAsyncApiChannels.scala` (an `AsyncApiChannelsProvider`
      registering one illustrative `"$domain_name$-events"` channel + generic `ExampleEvent`
      payload)
  - [ ] Red: `ExampleAsyncApiChannelsSuite.scala` asserting the provider's `channels` list
        contains the expected topic/eventType
  - [ ] Green: implement `ExampleAsyncApiChannels`
- [ ] Task 1.3: Wire `PureKafkaDocsPlugin` into `build.sbt` (`enablePlugins`,
      `asyncApiChannelsClass`/`asyncApiTitle`/`asyncApiVersion`)
- [ ] Task 1.4: Serve the raw AsyncAPI spec from `Main`'s routes (`/docs/asyncapi.json` via
      `purekafka.docs.Docs.routes`, matching order-service)
  - [ ] Red: a routes test asserting `/docs/asyncapi.json` returns 200 with the expected channel
  - [ ] Green: implement the route wiring
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Dependency pins + AsyncAPI wiring'
      (Protocol in workflow.md)

## Phase 2: Producer resilience example
- [ ] Task 2.1: Add `example-kafka-producer` config block to `application.conf` (same "not read
      by the live service" convention as `example-client`)
- [ ] Task 2.2: `KafkaProducerResilienceExampleSuite.scala`
  - [ ] Red: tests against a fake publish function — transient-failure-then-success (retried,
        succeeds), and sustained failure (circuit breaker opens)
  - [ ] Green: implement the example composition (`Resilience.middleware` + PureConfig-loaded
        `ResilienceConfig`)
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Producer resilience example'
      (Protocol in workflow.md)

## Phase 3: Consumer supervision + DLQ example
- [ ] Task 3.1: `KafkaConsumerExampleSuite.scala`
  - [ ] Red: tests proving (a) a crashing synthetic `Stream` gets restarted via
        `StreamSupervisor`, (b) a permanently-failing message is routed to the DLQ via
        `DeadLetter` — mirroring purekafka's own `StreamSupervisorSuite`/`DeadLetterSuite`
        synthetic-stream convention, no Docker Kafka
  - [ ] Green: implement the example
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Consumer supervision + DLQ example'
      (Protocol in workflow.md)

## Phase 4: Outbox-CDC example
- [ ] Task 4.1: `V2__create_outbox_events.sql` migration (copy order-service's generic schema:
      id/aggregate_id/payload/trace_parent/correlation_id/created_at)
  - [ ] Red: a migrations test asserting the `outbox_events` table exists post-migration,
        alongside the existing `V1` table
  - [ ] Green: add the migration
- [ ] Task 4.2: `OutboxExampleSuite.scala` — a self-contained, Testcontainers-Postgres-backed
      example demonstrating a same-transaction insert into both the base entity's table and
      `outbox_events` (does not touch the real `$domain_name$Store`)
  - [ ] Red: write the test
  - [ ] Green: implement the example
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Outbox-CDC example' (Protocol in
      workflow.md)

## Phase 5: Documentation + final verification
- [ ] Task 5.1: `docs/examples.md` — all four examples (HTTP `ClientResilienceExampleSuite` +
      the three new Kafka ones): what each demonstrates, how to promote to production code, how
      to remove cleanly (flagging the outbox migration's append-only caveat)
- [ ] Task 5.2: Short pointer section in `README.md` linking to `docs/examples.md`, same style
      as the "Developing the template" section
- [ ] Task 5.3: `./scripts/dev-regenerate.sh --domain-name devcheck` → confirm `sbt scalafmt
      test` green (including all new example suites) → confirm `sbt compile` produces a real,
      non-empty `target/asyncapi-docs-spec/asyncapi.json` + `target/asyncapi-docs/index.html`
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Documentation + final verification'
      (Protocol in workflow.md)
