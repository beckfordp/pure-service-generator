# Writing `Main`

A generated service's `Main.scala` acquires every dependency, builds the routes, and serves
them. This doc is the one place that convention is written down — follow it whether you're
editing the base template (`src/main/g8/`) or hand-extending a generated service with something
the template doesn't produce (an HTTP client, a Redis cache, a Kafka producer/consumer).

## The rule

`Main` is a single flat `Resource` for-comprehension — one `<-` per dependency, no nested
`Resource#use` callbacks. Every dependency that isn't already a `Resource` gets acquired via a
`.resource` method (or `.loadValidated`, for config) on its own owning type, instead of calling
`Resource.eval` on that type's internals directly inside `Main`. Two exceptions:

- A dependency that's **already `Resource`-returning** gets no wrapper — e.g. http4s'
  `EmberClientBuilder`/`EmberServerBuilder`. Wrapping those in a same-named `.resource` method
  would just be an extra indirection around something that's already the right shape.
- A **bare log line** (the "service starting" line) stays an explicit `Resource.eval` in `Main`
  — logging isn't a dependency, so it's excluded from the convention.

## Why

This is the same answer the wider http4s/cats-effect ecosystem reaches for instead of the Cake
pattern at real fan-out — see purerest's
[`docs/functional-design-patterns-scala-haskell-category-theory.md`](https://github.com/beckfordp/purerest/blob/main/docs/functional-design-patterns-scala-haskell-category-theory.md#scaling-tagless-final-without-cake-bundling-dependencies-into-resource-built-modules)
("Scaling tagless final without Cake: bundling dependencies into `Resource`-built modules") for
the full rationale — plain tagless final, plain `Resource`, just grouped so the parameter count
per layer stays small instead of growing with the whole dependency graph.

## The minimal example: the base template

`src/main/g8/src/main/scala/$package$/Main.scala` is the smallest instance of the pattern —
config, migrations, telemetry, the store, the server, nothing optional:

```scala
val run: IO[Unit] = (for {
  (config, port) <- Resource.eval($domain_name;format="cap"$ServiceConfig.loadValidated[IO])
  _              <- Migrations.resource[IO](config.postgres)
  telemetry      <- Telemetry.resource[IO](config.serviceName, config.metricsPort)
  _              <- Resource.eval(telemetry.logger.info(/* ... */)("$domain_name$-service starting"))
  store          <- $domain_name;format="cap"$Store.postgres[IO](config.postgres, telemetry.meter)
  routes          = /* ... */
  _              <- EmberServerBuilder.default[IO] /* ... */ .build
} yield ()).useForever
```

`Telemetry` (`src/main/g8/src/main/scala/$package$/Telemetry.scala`) is the one bundling module
the base template needs: it groups `tracer`/`meter`/`logger` — three dependencies every layer of
`Main` ends up needing — behind one `Telemetry.resource[F](serviceName, metricsPort)` call,
instead of three separate nested acquisitions.

## The fuller example: order-service

Once a generated service grows optional dependencies, the same convention just adds more lines,
not more structure. [`order-service`'s `Main.scala`](https://github.com/beckfordp/order-service/blob/main/src/main/scala/orderservice/Main.scala)
is the reference for this — it adds an HTTP client (`InventoryClient`), a Redis read-through
cache (`OrderHistoryCache`), and a supervised Kafka consumer group (`KafkaConsumers`) on top of
the same base shape, each still one `<-` line acquired via its own type's `.resource` method:

```scala
val run: IO[Unit] = (for {
  (config, port) <- Resource.eval(OrderServiceConfig.loadValidated[IO])
  _              <- Migrations.resource[IO](config.postgres)
  telemetry      <- Telemetry.resource[IO](config.serviceName, config.metricsPort)
  _              <- Resource.eval(/* starting log */)
  store          <- OrderStore.postgres[IO](config.postgres, telemetry.meter)
  _              <- KafkaConsumers.resource[IO](config.kafka, store, telemetry.logger, telemetry.meter)
  httpClient     <- EmberClientBuilder.default[IO].build   // already a Resource - no wrapper
  inventoryClient = InventoryClient.resilient[IO](httpClient, config.inventoryClient, telemetry)
  historyCache   <- OrderHistoryCache.resource[IO](config.redis, telemetry.logger)
  routes          = /* ... */
  _              <- EmberServerBuilder.default[IO] /* ... */ .build
} yield ()).useForever
```

Nothing about the shape changes with fan-out — that's the point of the convention.

## When several dependencies are the same shape

If a service ends up with several same-shaped dependencies (several HTTP clients calling sibling
services, say), bundle them into one case class the same way `Telemetry` bundles
tracer/meter/logger — purerest's design doc covers this directly as the
[`Clients[F]` pattern](https://github.com/beckfordp/purerest/blob/main/docs/functional-design-patterns-scala-haskell-category-theory.md#scaling-tagless-final-without-cake-bundling-dependencies-into-resource-built-modules).
Don't re-derive it; that section already works through the shopping-cart-style example in full.

## Related docs

- [`docs/developing-the-template.md`](./developing-the-template.md) — the generate → edit → diff
  → port-back workflow for changing anything under `src/main/g8/`, including `Main.scala` itself.
- [`README.md`](../README.md) — generating a service, the known giter8 gotchas.
