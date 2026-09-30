# Plan: Add a --private flag to the publish pipeline

## Phase 1: Implement the flag
- [x] Task: Add `--private` to `scripts/generate-and-publish-service.sh`'s argument parsing and
      `--help` text; update the `gh repo create` step to pass `--private` when set, `--public`
      otherwise (unchanged default). Verify locally: `--help` output shows the new flag,
      `bash -n` syntax check passes. `5cb2b07`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Implement the flag' (Protocol in
      workflow.md) - verified directly (prompting off): `--help` output includes `--private`,
      `bash -n` passes.

## Phase 2: Live verification
Real, external side effects (creates public/private GitHub repos) — proceeds directly per
explicit user decision, disposable timestamped repo names, deleted after.
- [ ] Task: Run with `--private` against a disposable domain name; confirm via
      `gh repo view --json visibility` that the repo is actually private, that it pushed, and
      that its GitHub Actions CI run passes.
- [ ] Task: Run *without* `--private` against a second disposable domain name; confirm the repo
      is still public (regression check on the unchanged default) and CI passes.
- [ ] Task: Delete both throwaway repos created during this phase's verification.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Live verification' (Protocol in
      workflow.md)

## Phase 3: Documentation
- [ ] Task: Update the README's "Visibility" subsection (under "Automated generate → publish →
      CI pipeline") to document `--private` and confirm the documented default (public) still
      matches behavior.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Documentation' (Protocol in
      workflow.md)
