# Project Tracks

This file tracks all major tracks for the project.

- [~] **Track: Add a --private flag to the publish pipeline (scripts/generate-and-publish-service.sh)**
  *Link: [./tracks/private-repo-flag_20260930/](./tracks/private-repo-flag_20260930/)*

---

## Backlog

Title-only placeholders for future tracks — not yet detailed (no spec/plan, no linked
folder), so `/conductor:implement` cannot pick these up by accident. Reorder freely as
priorities change. When ready to work on one, run `/conductor:newTrack <title>` to go
through the spec/plan questions and promote it into a real track below.

- Add CORS support to the generated service template — no generated
  service sends an `Access-Control-Allow-Origin` header today, confirmed
  across all six already-generated Gluon services (catalog/cart/order
  confirmed directly via browser testing from a real frontend; the other
  three inferred, same template). `curl` gets a clean response with an
  `Origin` request header set, but a browser blocks the equivalent
  `fetch()` as cross-origin. Fix point:
  `src/main/g8/src/main/scala/$package$/Main.scala` — `routes` is
  composed as `ServerMetrics.middleware(meter)(ServerTracing.middleware(tracer)(docsRoutes))`
  then `.withHttpApp(routes.orNotFound)`; wrap that chain with http4s's
  own `org.http4s.server.middleware.CORS` before `.orNotFound`. One
  generator-level fix, then backport to each already-generated service
  (tracked per-service as each repo's own top backlog item — see
  `gluon/TECHNICAL_DEBT.md` TD-1 and `gluon/docs/adr/` for the originating
  discussion).

---
