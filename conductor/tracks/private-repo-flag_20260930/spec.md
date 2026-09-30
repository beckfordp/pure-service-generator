# Spec: Add a --private flag to the publish pipeline

## Overview
Adds an optional `--private` boolean flag to `scripts/generate-and-publish-service.sh`, so a
generated service can be published as a private GitHub repo instead of the current
always-public behavior. Default stays public (backward compatible with existing usage and the
README's documented default).

## Functional Requirements
1. **New flag**: `--private` (boolean, no value) added to the script's argument parsing and
   `--help` text.
2. **Repo creation**: `gh repo create ... --public` becomes `gh repo create ... --public` (default)
   or `gh repo create ... --private` (when `--private` is passed) — mutually exclusive, chosen
   based on the flag.
3. **Everything else unchanged**: the collision check, local commit, `GH_PACKAGES_TOKEN` secret,
   push, and wait-for-CI steps behave identically regardless of visibility.

## Non-Functional Requirements
- No change to default behavior when `--private` is omitted — existing callers/docs remain
  accurate without modification beyond noting the new flag exists.

## Acceptance Criteria
- `--help` documents `--private`.
- A live run with `--private` against a disposable domain name creates an actual private GitHub
  repo (verified via `gh repo view --json visibility`), pushes, and its GitHub Actions CI run
  passes — proven for real, then the repo deleted, matching this project's established live-
  verification pattern.
- A live run *without* `--private` still creates a public repo (regression check that the new
  flag didn't change the default).

## Out of Scope
- `--internal` visibility (GitHub Enterprise/org-only concept, not relevant to a personal
  account).
- A way to configure a project-wide default visibility (e.g. via a config file) — the flag is
  the only lever.
