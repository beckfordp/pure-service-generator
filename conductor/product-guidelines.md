# Product Guidelines

## Functional Programming Principles (project-wide)
- Domain errors are modeled as sealed traits/ADTs (typed errors), never as exceptions used for
  control flow. Exceptions are reserved for truly unrecoverable failures (bugs, environment
  failures) — never for an expected, nameable failure case.
- No exceptions for control flow, anywhere — errors are always represented in the return type
  (`Either`, `EitherT`, or an equivalent typed result), all the way from the point of failure to
  the HTTP edge.
- This scopes to *our own* domain/application errors — an expected, nameable failure case we
  can enumerate (not found, invalid input, a downstream dependency unavailable, and so on). It
  does not require eliminating exceptions that are part of a third-party framework's own API
  contract (e.g. http4s's `Client[F]`/`HttpRoutes[F]` are inherently `MonadError`/
  `raiseError`-based) or genuine system/environment failures (a config load failure at startup,
  a JVM-level error). The obligation is to catch our own expected failure modes at the point
  they're known and convert them to a typed result before they reach a caller or an HTTP
  response — not to eliminate every `Throwable` from the codebase.
- No partial functions on the happy path — no `Option.get`/`Either.right.get`, no `head` on a
  possibly-empty collection, no non-exhaustive pattern match relying on a runtime `MatchError`.
  Prefer pattern matching, `fold`, or explicit handling of every case.
- No `null`, no mutable `var` in domain code — `Option`/`Either`/immutable data structures
  throughout; `Ref`/`Deferred` (cats-effect) for state that genuinely needs to change, never a
  bare mutable field.
- Any inherently impure operation (randomness, wall-clock time, mutable state, a third-party
  library's synchronous/side-effecting API) is captured in the effect type (`F[_].delay`, or a
  dedicated capability like `Random[F]`/`Clock[F]`) at the point it happens — never executed as
  a bare side effect and only wrapped incidentally.
- HTTP-facing endpoints map typed domain errors explicitly to HTTP status codes at the edge — no
  implicit/central exception-to-status translation for domain errors.

## Dependency on purerest
- purerest (tracing, structured logging, metrics, retry, circuit breaker, tapir-based
  self-documenting endpoints) is consumed as a **published dependency** resolved from GitHub
  Packages (`io.github.beckfordp` `purerestlib`), never vendored or depended on via source —
  this project is purerest's first genuine external consumer, and stays that way.
- Public APIs this project builds on top of purerest follow the same tagless-final /
  typeclass-based style (`F[_]: Async`, etc.) as purerest itself. Consumers of *this* project's
  own algebras wire them in via explicit combinators/constructor parameters, not annotations,
  reflection, or macros.

## Persistence: one database per service
- Each service owns exactly one dedicated Postgres database, named after the service's domain
  (singular — e.g. the order domain's database and its primary table are both named `order`,
  not `orders`). No service reads or writes another service's database directly.

## Code Style
- Formatted with scalafmt, default Scala 3 style.

## API Conventions
- Plain resource JSON responses with standard HTTP status codes — no envelope spec (e.g. no
  JSON:API).
- Errors returned as a small JSON error object whose HTTP status matches the mapped domain
  error.
- Every service exposes `GET /health` (liveness) and `GET /health/ready` (readiness, verifying
  real dependency connectivity such as the database) in addition to its domain endpoints.

## Naming
- Services are named `<domain>-service` (e.g. `order-service`).
- The domain's Postgres database and primary table share the domain's singular name (e.g.
  `order`).
