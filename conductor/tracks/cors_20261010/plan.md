# Plan: Add CORS support to the generated service template

## Phase 1: Implement CORS middleware (TDD)
- [x] Task: Write failing tests in a new
      `src/main/g8/src/test/scala/$package$/CorsSuite.scala` (following the
      `$domain_name;format="cap"$DocsSuite` pattern of testing routes directly). Assert: (a) a
      `GET` request carrying an `Origin` header, run through `Cors.middleware`-wrapped routes,
      gets back `Access-Control-Allow-Origin` matching that origin; (b) an `OPTIONS` preflight
      request (`Origin` + `Access-Control-Request-Method: POST`) gets a success status with
      `Access-Control-Allow-Methods` containing `GET`/`POST`/`PATCH`/`PUT`/`DELETE` and
      `Access-Control-Allow-Headers` containing `Content-Type`; (c) no
      `Access-Control-Allow-Credentials` header is present (or it's absent/false). Run
      `sbt test` and confirm it fails (the referenced `Cors` object doesn't exist yet — Red
      phase).
- [x] Task: Implement `src/main/g8/src/main/scala/$package$/Cors.scala` — a small
      `Cors.middleware[F[_]]` wrapping http4s's `org.http4s.server.middleware.CORS.policy`
      builder: allow-all origins, methods `GET`/`POST`/`PATCH`/`PUT`/`DELETE`, header
      `Content-Type`, credentials disallowed. Run `sbt test`, confirm `CorsSuite` now passes
      (Green phase).
- [x] Task: Wire `Cors.middleware` into `Main.scala`, wrapping the fully-composed `routes`
      value (tracing + metrics + docs) before `.withHttpApp(routes.orNotFound)`, so every
      response — including 404s/validation errors — carries CORS headers. Run the full
      `sbt test` suite, confirm no regressions. Run `sbt coverage test coverageReport`, confirm
      `Cors.scala` is covered.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Implement CORS middleware' (Protocol
      in workflow.md) — prompting is off for this repo (`.claude/prompt-off`); verified via the
      new committed `scripts/verify-cors.sh` instead of an interactive walkthrough (see Phase 2).

## Phase 2: End-to-end verification & documentation
- [x] Task: Regenerate a disposable instance via `scripts/dev-regenerate.sh`; run its full test
      suite; confirm green with no regressions from the template change.
- [x] Task: Start the regenerated instance (`docker compose up -d && sbt run`) and manually
      verify real HTTP behavior: `curl -i -H "Origin: http://example.com" <GET endpoint>` shows
      `Access-Control-Allow-Origin` in the response; an `OPTIONS` preflight `curl` against one
      of the write endpoints shows the expected
      `Access-Control-Allow-Methods`/`Access-Control-Allow-Headers`.
- [x] Task: Document the new default in `README.md` (new subsection documenting the CORS policy
      — allow-all origins, methods/headers matched to the exposed routes, no credentials — and
      the rationale) and in `conductor/tech-stack.md`'s HTTP section.
- [x] Task: Conductor - User Manual Verification 'Phase 2: End-to-end verification &
      documentation' (Protocol in workflow.md) — `scripts/verify-cors.sh` regenerates a scratch
      instance, starts it for real (Postgres + `sbt run`), and asserts the CORS headers
      (Access-Control-Allow-Origin, -Methods, -Headers, absence of -Credentials) on live HTTP
      responses. Run and passed: 55/55 tests, 100% statement/branch coverage on `Cors.scala`
      (88.67%/89.19% overall), real GET/OPTIONS checks against a running instance confirmed.
