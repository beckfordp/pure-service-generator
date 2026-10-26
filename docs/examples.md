# Generated examples

Every generated service ships a handful of self-contained, tested examples demonstrating a
resilience/observability pattern the template itself doesn't wire into production code by
default — the generated service has no outbound HTTP call, no Kafka producer/consumer, and no
Kafka event to announce, so there's nothing real for these patterns to attach to until you add
one. Each example is real, compiled, tested code (not a comment or a snippet) — copy it, adapt
it, or delete it, as your service's needs turn out to be.

Four of the five live in `src/test/scala/$package$/examples/` and are pure test-space: deleting
the file is the whole removal story. The fifth (AsyncAPI docs) is main-code, since the
`sbt-purekafka-docs` plugin needs a real compiled class to point at — its removal section below
is accordingly a little more involved.

## HTTP client resilience

**File:** `src/test/scala/$package$/examples/ClientResilienceExampleSuite.scala`

Demonstrates wrapping an http4s `Client[F]` with purerest's `Resilience.middleware` (retry +
circuit breaker) before building a downstream client on top of it — against a dummy client, not
a real call. Two tests: a transient failure retried to success, and a sustained failure that
trips the circuit breaker.

- **Promote to production:** when you add a real downstream call, wrap its `Client[F]` the same
  way inside `Main` (or wherever you construct the client), loading a `ResilienceConfig` via
  PureConfig from your own named `application.conf` block, same as this example's
  `example-client`.
- **Remove if unused:** delete the test file and the `example-client` block in
  `application.conf`.

## Kafka producer resilience

**File:** `src/test/scala/$package$/examples/KafkaProducerResilienceExampleSuite.scala`

Same pattern as the HTTP example, using `purekafka.resilience.Resilience.middleware` around a
generic `F[A] => F[A]` publish call instead of an http4s `Client[F]` — against a dummy publish
function. Relevant if you ever call a Kafka producer directly, outside the outbox-CDC pattern
below.

- **Promote to production:** wrap your real publish call the same way; config lives in
  `example-kafka-producer` in `application.conf`.
- **Remove if unused:** delete the test file and the `example-kafka-producer` block in
  `application.conf`.

## Kafka consumer supervision + DLQ

**File:** `src/test/scala/$package$/examples/KafkaConsumerExampleSuite.scala`

Demonstrates wrapping a consumer `Stream[F, O]` in `purekafka.consumer.StreamSupervisor`
(restart-with-backoff if the whole stream dies — a broker disconnect) with each message's
processing guarded by `purekafka.consumer.DeadLetter` (bounded retry, then route to
`<topic>-dlq` instead of killing the stream for one poison message) — against synthetic
messages, no real Kafka broker involved. Two tests: a permanently-unprocessable message routed
to the DLQ while the stream keeps consuming, and a stream-level crash restarted with backoff.

- **Promote to production:** wrap your real `fs2-kafka` consumer's
  `consumer.stream.evalMap(processRecord)` pipeline the same way. See order-service's
  [`KafkaConsumers.scala`](https://github.com/beckfordp/order-service/blob/main/src/main/scala/orderservice/KafkaConsumers.scala)
  for the fuller, real-topic version of this exact composition, including loading
  `StreamSupervisorConfig`/`RetryConfig`/`ResilienceConfig` from `application.conf` rather than
  the hardcoded fast test constants this example uses.
- **Remove if unused:** delete the test file. No config, no migration tied to it.

## Outbox-CDC

**Files:** `src/test/scala/$package$/examples/OutboxExampleSuite.scala`,
`src/main/resources/db/migration/V2__create_outbox_events.sql`

Demonstrates the transactional outbox pattern (ADR 0008 in gluon): writing the Kafka-bound
event to an `outbox_events` table in the *same* Postgres transaction as the state change it
announces, instead of a separate, non-transactional direct publish call — a Kafka Connect +
Debezium connector streams new rows from that table to Kafka, unchanged. Two tests: the
happy-path same-transaction write, and a rollback proof (forcing the outbox write to fail and
confirming the paired entity write never persisted either).

- **Promote to production:** add the same-transaction `outbox_events` insert to your real
  `$domain_name;format="cap"$Store`'s state-changing methods (inside the same
  `session.transaction.use` block as the state-change statement itself). See order-service's
  [`OrderStore.scala`](https://github.com/beckfordp/order-service/blob/main/src/main/scala/orderservice/OrderStore.scala)
  (`insertOutboxRow`) for the fuller, real version.
- **Remove if unused:** delete the test file freely. **Do not delete
  `V2__create_outbox_events.sql`** once it's been applied anywhere real — Flyway migrations are
  append-only history, not a toggle. If the table turns out to be genuinely unused, leave it in
  place (harmless, empty) or write a *new* migration to drop it; never delete an already-applied
  migration file.

## AsyncAPI docs

**File:** `src/main/scala/$package$/examples/ExampleAsyncApiChannels.scala`

The one example that's main-code, not test-space: `sbt-purekafka-docs`'s `asyncApiChannelsClass`
build setting needs a real, compiled `AsyncApiChannelsProvider` singleton to reflectively load,
so it can't live in `src/test/`. Registers one illustrative channel (`ExampleEvent` on
`$domain_name$-events`) — including a `status` field demonstrating the enum-validator pattern
(`sttp.tapir.Validator.enumeration`, which shows up as a real `"enum": [...]` constraint in the
generated AsyncAPI JSON Schema) — purely so the AsyncAPI pipeline this template wires in by
default has something real to render, out of the box, instead of an empty document.

This pipeline runs two ways: `sbt compile` regenerates
`target/asyncapi-docs-spec/asyncapi.json` + `target/asyncapi-docs/index.html` automatically
(via `PureKafkaDocsPlugin`, `build.sbt`), and `AppRoutes` serves the same JSON live at
`/docs/asyncapi.json`, plus the rendered HTML at `/docs/asyncapi/index.html` (from
`target/asyncapi-docs`, overridable with `ASYNCAPI_HTML_DIR`).

A third task, `schemaRegistryCheck` (Rework 5 step 3/4, ADR 0003), reuses the same
`ExampleAsyncApiChannels` registration to check/register each channel's JSON Schema against an
Apicurio Registry instance (`schemaRegistryUrl`/`schemaRegistryGroupId` in `build.sbt`), failing
the build on an incompatible change. Unlike `asyncApiGenerate`, it's *not* auto-chained after
`Compile / compile` — it makes a real network call, so `sbt compile`/`sbt test` keep working even
when no registry is running. Run it explicitly (`sbt schemaRegistryCheck`), e.g. in CI — see
`.github/workflows/ci.yml`'s throwaway `docker run` Apicurio container step.

- **Promote to production:** once you register real Kafka channels (e.g. alongside the producer
  or consumer examples above, once promoted), replace `ExampleAsyncApiChannels`'s illustrative
  channel with your real ones in the same file (or a new one, updating `build.sbt`'s
  `asyncApiChannelsClass` to match) — this automatically carries through to `schemaRegistryCheck`
  too, since both tasks read the same registration. See order-service's
  [`AsyncApiChannels.scala`](https://github.com/beckfordp/order-service/blob/main/src/main/scala/orderservice/AsyncApiChannels.scala)
  for a real, multi-channel registration, and its
  [`schemaRegistryCheck` adoption](https://github.com/beckfordp/order-service/blob/main/build.sbt)
  in the same `build.sbt`.
- **Remove entirely if you don't want AsyncAPI docs (or the schema registry check):**
  1. Delete `ExampleAsyncApiChannels.scala`.
  2. In `build.sbt`: drop `PureKafkaDocsPlugin` from the root project's `.enablePlugins(...)`
     call, and remove the `asyncApiChannelsClass`/`asyncApiTitle`/`asyncApiVersion`/
     `schemaRegistryUrl`/`schemaRegistryGroupId` settings.
  3. In `AppRoutes.scala`: drop the `asyncApiRoutes`/`staticAsyncApiRoutes` values and their
     `<+>`s into `tracedRoutes`, drop the `channels` and `staticAsyncApiDocsDir` parameters, and
     remove the now-unused `purekafka.docs.*`, `fs2.io.file.Files`, and
     `org.http4s.server.staticcontent.*` imports. In `Main.scala`: drop the
     `$package$.examples.ExampleAsyncApiChannels` import and the `channels` argument to
     `AppRoutes.all`.
  4. In `.github/workflows/ci.yml`: drop the "Start/Stop a throwaway Apicurio Registry" and
     "schemaRegistryCheck" steps.
  5. If nothing else in your service uses `purekafka` either (i.e. you also removed the
     producer/consumer examples above without promoting them), drop the `purekafka`/`fs2-kafka`
     library dependencies and the `sbt-purekafka-docs` plugin dependency from
     `build.sbt`/`project/plugins.sbt` too.

## Related docs

- [`docs/developing-the-template.md`](./developing-the-template.md) — the workflow for changing
  anything under `src/main/g8/`, including these examples.
- [`docs/writing-main.md`](./writing-main.md) — the `Resource`-per-dependency convention `Main`
  follows, relevant if you promote any of these into `Main` itself.
