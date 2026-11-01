# Spec — Template: REST docs convention (gluon TD-20.2)

## Overview
Apply purerest's REST docs convention (README "Documenting endpoints", purerestlib 0.7.3) to
the g8 template so every generated service's `/docs` describes each endpoint, parameter, DTO
and error, and a coverage test keeps it at zero gaps. Wording approved 2026-11-01 (`widget` =
`$domain_name$`).

## Functional Requirements
1. purerestlib 0.4.0 → 0.7.3; fix any API drift.
2. Tags: `widgets` (CRUD), `health`.
3. Endpoint summary — description:
   - POST /widgets — Create a widget — Creates a widget from the body and returns it with its
     server-assigned id and timestamps. Not idempotent: each call creates a new widget.
   - GET /widgets — List widgets — Returns a page of widgets, newest first, with the total in
     `X-Total-Count`. Read-only. 400 if `limit`/`offset` is out of range.
   - GET /widgets/{id} — Get a widget — Returns the widget with this id. Read-only. 404 if none.
   - PATCH /widgets/{id} — Update a widget — Sets the widget's updatable fields from the body
     and returns it. Every updatable field is required (same as PUT). Idempotent. 404 if none.
   - PUT /widgets/{id} — Replace a widget — Replaces the widget's updatable fields with the body
     and returns it. Idempotent. 404 if none.
   - DELETE /widgets/{id} — Delete a widget — Deletes the widget with this id. 404 if none, so a
     repeat delete returns 404.
   - GET /health — Liveness check — 200 while the process is up. Checks no dependencies.
   - GET /health/ready — Readiness check — 200 when the store answers a ping, 503 when it
     doesn't.
4. Parameters:
   - `id` — "Widget id (UUID). A malformed id returns 400." — ex
     `3f2b8c1e-7a4d-4e9b-9c55-0d6e2a1b7f40`
   - `limit` — "Page size, 1–100. Defaults to 20." — ex `20`
   - `offset` — "Widgets to skip before the page starts, ≥ 0. Defaults to 0." — ex `0`
   - `X-Total-Count` header — "Total widgets across all pages."
5. DTOs:
   - `CreateWidgetRequest` — "Fields for a new widget."
   - `UpdateWidgetRequest` — "A widget's updatable fields; every one is required."
   - `WidgetResponse` — "A widget as stored."
     - `id` — "Widget id (UUID)." — ex as above
     - `createdAt` — "When the widget was created (UTC)." — ex `2026-11-01T09:30:00Z`
     - `updatedAt` — "When the widget was last changed (UTC)." — ex `2026-11-01T09:45:00Z`
   - `ErrorResponse` — "Error body."
     - `error` — "What went wrong." — ex `Widget not found`
6. Errors:
   - 404 — "No widget with this id." — ex `{"error":"Widget not found"}`
   - 400 (list) — "`limit` outside 1–100 or `offset` negative." — ex
     `{"error":"Invalid pagination parameters"}`
   - 503 (ready, no body) — "The store didn't answer a ping."
7. Codegen: field-spec gains required `description`; CASE_CLASS_FIELD/CREATE_PARAMS/UPDATE_PARAMS
   render `@description("<description>") @encodedExample(<example>) name: Type` (Instant example
   as ISO string). Codegen README updated.
8. Coverage test in `WidgetDocsSuite`: `Docs.undocumented(<all server endpoints>.map(_.endpoint))`
   is `Nil`.

## Non-Functional
- Annotations only on API case classes (scala-fp.md §7 exception); scalafmtCheck clean; README
  notes the convention for new endpoints.

## Acceptance Criteria
- Template + codegen suites green.
- Scratch devcheck-service: generated suite green with zero doc gaps, both bare and after codegen
  with a sample field-spec (with descriptions); `/docs/docs.yaml` shows
  summaries/descriptions/examples.
- Empty create/update request passes the example check (class-level `@encodedExample` if needed).

## Out of Scope
- Service repos (TD-20.3–20.10), system-design note (TD-20.11), TD-12 no-CRUD mode, client-side
  mirror DTOs.
