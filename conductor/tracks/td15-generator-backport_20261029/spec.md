# TD-15 generator backport — pure-FP platform baseline

## Overview

Port the patterns proven in order-service (TD-15.1 + review fixes) into the
`pure-service-generator` template so future services start with typed ids,
config-driven AsyncAPI docs, ScalaCheck, and narrowed effect bounds.

## Functional Requirements

1. **Typed entity id** — opaque `<Entity>Id` in a `domain/` package, parsed at
   the route boundary; store/route signatures typed; malformed → 400.
2. **Config** — `asyncApiHtmlDir` with `${?ASYNCAPI_HTML_DIR}`; no `sys.env`.
3. **ScalaCheck** — `munit-scalacheck` dependency + id property suite.
4. **Effect constraints** — `Cors` → `Monad`; `HealthRoutes` health →
   `Applicative`, ready/routes → `Monad`; CRUD handlers narrowed.
5. **Generated tests** updated for typed ids and malformed-id 400.

## Acceptance Criteria

- A freshly generated service compiles, passes its suite, and exposes the
  configured AsyncAPI dir with no raw UUID parsing in its store.
