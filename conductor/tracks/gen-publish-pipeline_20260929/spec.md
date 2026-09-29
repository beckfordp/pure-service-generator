# Spec: Automated generation → repo-creation → push → CI pipeline

## Overview
A single CLI script that takes a domain name (and optional package/field-spec overrides),
generates a new service from this repo's giter8 template, optionally extends it via the
field-codegen tool, creates a new public GitHub repo under the same account, pushes the
generated service, wires up GitHub Actions CI (including GitHub Packages credentials so it can
resolve `purerestlib`), and waits for that CI run to go green — this project's answer to
`service-generator`'s Jenkins-based pipeline, reimplemented without Jenkins/Groovy.

## Functional Requirements
1. **`scripts/generate-and-publish-service.sh`** — args: `--domain-name` (required),
   `--package` (optional, passed through to giter8), `--field-spec` (optional path, passed to
   `tools/codegen`), `--repo-name` (optional, defaults to `<domain_name>-service`).
2. **Generate**: reuse the existing `giter8.LauncherMain` pattern (as in
   `scripts/verify-g8-template.sh`) to generate the service into a local working directory.
3. **Optional field-codegen**: if `--field-spec` is given, run `tools/codegen`'s `Main` against
   the generated directory before proceeding (as in `scripts/verify-codegen-tool.sh`).
4. **CI workflow template**: add `.github/workflows/ci.yml` to the giter8 template
   (`src/main/g8/`) — checkout, set up JDK/sbt, run `sbt scalafmtCheck Test/scalafmtCheck test`
   on push. `GITHUB_ACTOR` comes from the automatic `${{ github.actor }}`; `GITHUB_TOKEN` (used
   by the generated build to resolve `purerestlib`) comes from a **repository secret** (not the
   automatic, same-repo-scoped built-in `GITHUB_TOKEN`, which likely can't read another repo's
   packages) — `GH_PACKAGES_TOKEN`.
5. **Repo creation**: `gh repo create <owner>/<repo-name> --public`, using whichever account
   the caller's `gh` is authenticated as (this session: `beckfordp`). Abort with a clear error
   if a repo with that name already exists — no overwrite.
6. **Push**: init git in the generated directory, commit, push to the new repo's default
   branch.
7. **CI secret**: `gh secret set GH_PACKAGES_TOKEN --repo <owner>/<repo-name>`, reusing the
   caller's own `gh auth token` (matching how every other script in this repo already resolves
   `GITHUB_TOKEN`) — set *before* the push that triggers the first CI run, so that run can
   actually resolve packages.
8. **Wait for CI**: after pushing, poll (`gh run watch` or an equivalent polling loop) until the
   triggered Actions run completes; report pass/fail clearly.

## Non-Functional Requirements
- This is the first track in this project whose verification has a real, external, hard-to-
  reverse effect (creates a public GitHub repo, uses the caller's `gh` credentials). Per
  explicit user decision, the live end-to-end verification run proceeds under normal
  implementation flow (no extra approval gate), using an obviously-disposable repo name (e.g.
  timestamped), and the script/verification cleans up (`gh repo delete`) the throwaway repo
  afterward.
- No change to the existing `src/main/g8/` domain/CRUD template content — this track only adds
  the CI workflow file and the new orchestration script.

## Acceptance Criteria
- Running `scripts/generate-and-publish-service.sh --domain-name <x>` end-to-end: generates the
  service, creates a real public repo, pushes it, sets the CI secret, and the resulting GitHub
  Actions run passes (green) on the first attempt — proven with a real, disposable-named repo
  during implementation, then deleted.
- Running with `--field-spec <path>` additionally applies the field-codegen tool before pushing,
  and CI still passes.
- Running with a `--repo-name` that already exists on GitHub aborts cleanly with a clear error
  message, without touching the existing repo.

## Out of Scope
- Private-repo support (this track defaults to public only; a `--private` flag could be a future
  addition).
- Any change to which CI provider is used (GitHub Actions only).
- Automatic cleanup/deletion of repos created for real (non-verification) use — only the
  throwaway repo created during this track's own verification is deleted.
