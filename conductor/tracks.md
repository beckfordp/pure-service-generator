# Project Tracks

This file tracks all major tracks for the project.

---

## Backlog

Title-only placeholders for future tracks — not yet detailed (no spec/plan, no linked
folder), so `/conductor:implement` cannot pick these up by accident. Reorder freely as
priorities change. When ready to work on one, run `/conductor:newTrack <title>` to go
through the spec/plan questions and promote it into a real track below.
- Make the base entity fields (currently fixed `item`/`quantity`/`status` on top of
  `id`/`createdAt`/`updatedAt`) themselves field-spec-driven, so a generated service isn't
  stuck with order-specific fields for non-order domains. Needs per-field create/update
  visibility in the field model (deferred out of field-codegen_20260929 v1, which assumes
  every field appears in create/update/response uniformly) — see
  `conductor/archive/field-codegen_20260929/spec.md`.

---
