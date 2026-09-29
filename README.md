# pure-service-generator

A production-quality reference microservice built on
[purerest](https://github.com/beckfordp/purerest), and eventually a generator for new
purerest-based services.

Work is tracked via the [Conductor](./conductor/index.md) framework. See
[`conductor/product.md`](./conductor/product.md) for the vision and current iteration goals, and
[`conductor/tracks.md`](./conductor/tracks.md) for in-progress/planned work. This README will be
replaced with real quickstart/usage instructions once the reference service exists
(track `pure-service-generator_20260929`, Phase 6).

## Calling other services with resilience

This reference service doesn't call any other service, so purerest's retry + circuit-breaker
middleware (`purerest.resilience.Resilience.middleware`) isn't wired into `Main`. When you adapt
this template to call a real downstream service, wrap its http4s `Client[F]` with
`Resilience.middleware` before building your own client on top of it — see
[`ClientResilienceExampleSuite`](./src/test/scala/orderservice/examples/ClientResilienceExampleSuite.scala)
for the pattern, tested against a dummy client so it stays correct as purerest evolves.
