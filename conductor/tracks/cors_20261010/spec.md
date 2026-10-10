# Spec: Add CORS support to the generated service template

## Overview
No generated service sends an `Access-Control-Allow-Origin` header today, confirmed directly
(browser testing from a real frontend) against the catalog/cart/order Gluon services and
inferred for the other three (same template). `curl` with an `Origin` header gets a clean
response, but a browser blocks the equivalent cross-origin `fetch()`. This track adds CORS
support to the g8 template itself (`src/main/g8/`) so every future service generated from it
serves correct CORS headers out of the box. Backport to the six already-generated services is
explicitly out of scope here — tracked per-service as each repo's own top backlog item (see
`gluon/TECHNICAL_DEBT.md` TD-1).

## Functional Requirements
1. Wrap the composed HTTP routes in `src/main/g8/src/main/scala/$package$/Main.scala` with
   http4s's `org.http4s.server.middleware.CORS` middleware, so every response (success and
   error) carries CORS headers.
2. **Allowed origins:** allow all origins (`Access-Control-Allow-Origin` reflects/allows any
   origin) — a static, zero-config default appropriate for a freshly generated service with no
   frontend origin decided yet.
3. **Allowed methods/headers:** allow exactly the methods the template's own routes expose —
   `GET`, `POST`, `PATCH`, `PUT`, `DELETE` — plus the `Content-Type` request header (needed for
   the JSON request bodies on `POST`/`PATCH`/`PUT`). `OPTIONS` preflight requests are handled by
   the middleware itself.
4. **Credentials:** do not allow credentials (`Access-Control-Allow-Credentials: false` /
   omitted) — matches the template's current no-auth posture, and is required anyway alongside
   an allow-all-origins policy (browsers reject credentialed requests against a wildcard
   origin).
5. Placement in the middleware chain: CORS wraps the full routes (tracing + metrics + docs),
   applied before `.withHttpApp(routes.orNotFound)`, so CORS headers are present on every
   response the server returns, including 404s and tapir validation errors.
6. Document the new middleware and its default policy in `README.md` (the template's "what a
   generated service includes" section) and in `conductor/tech-stack.md`.

## Non-Functional Requirements
- No new third-party dependency — `org.http4s.server.middleware.CORS` ships in `http4s-server`,
  already pulled in transitively via `http4s-ember-server` (confirmed at
  `http4sVersion = "0.23.37"`).
- Change is confined to the g8 template and its own tests/docs — no change to
  `tools/codegen/` or `scripts/generate-and-publish-service.sh`.

## Acceptance Criteria
- [ ] A new test suite (following the existing `*Suite.scala` pattern, e.g. testing the
  CORS-wrapped routes directly the way `$domain_name;format="cap"$DocsSuite` tests `Docs.routes`
  directly) proves: a simple cross-origin `GET`/`POST` request receives
  `Access-Control-Allow-Origin` in the response, and an `OPTIONS` preflight request for one of
  the five CRUD methods receives a `200`/`204` with the right
  `Access-Control-Allow-Methods`/`Access-Control-Allow-Headers`.
- [ ] Generating a fresh service from the template (`dev-regenerate.sh`) and running its full
  test suite passes with no regressions.
- [ ] A manual check (`curl -i` with an `Origin` header, or a browser `fetch()` against a
  locally running generated instance) shows the CORS header present on a real endpoint
  response.
- [ ] `README.md` and `conductor/tech-stack.md` mention the CORS default and that it's
  intentionally permissive (allow-all-origins, no credentials).

## Out of Scope
- Backporting this fix to the six already-generated Gluon services (catalog/cart/order
  confirmed, three others inferred) — each repo tracks this as its own backlog item.
- Making allowed-origins configurable via PureConfig/environment variable — the default is a
  static allow-all policy; a configurable allow-list is a possible future enhancement, not part
  of this track.
- Allowing credentials on cross-origin requests.
- Any change to `tools/codegen/` or the publish pipeline.
