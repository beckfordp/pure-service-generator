# Plan: Add a --private flag to the publish pipeline

> ⚠️ **PAUSED (2026-09-30):** Phase 2 live verification hit a real, external blocker —
> `gh repo create --private` succeeds, but the repo's first GitHub Actions run fails
> immediately with `startup_failure` (0s runtime). This is GitHub's private-repo Actions
> billing gate: public repos get free/unlimited GitHub-hosted-runner minutes, but private
> repos only get a limited monthly allowance, and once that's exhausted a non-zero Actions
> spending limit is required (the account default is $0) or every run fails at startup.
>
> **Action needed from you before this track can resume:** raise your Actions spending limit
> at <https://github.com/settings/billing/spending_limit>.
>
> In the meantime, Phase 1's code (commit `5cb2b07`) was rolled back in `ee7217d` since it
> can't be live-verified yet — see "Phase: Rollback" below. Once the spending limit is
> raised, re-apply the `--private` flag (the diff is preserved in `5cb2b07`) and resume from
> Phase 2.

## Phase 1: Implement the flag
- [x] Task: Add `--private` to `scripts/generate-and-publish-service.sh`'s argument parsing and
      `--help` text; update the `gh repo create` step to pass `--private` when set, `--public`
      otherwise (unchanged default). Verify locally: `--help` output shows the new flag,
      `bash -n` syntax check passes. `5cb2b07`
- [x] Task: Conductor - User Manual Verification 'Phase 1: Implement the flag' (Protocol in
      workflow.md) - verified directly (prompting off): `--help` output includes `--private`,
      `bash -n` passes.

## Phase: Rollback (2026-09-30)
- [x] Task: Revert `scripts/generate-and-publish-service.sh` to its pre-`--private` state
      (Phase 1's code from `5cb2b07`), since Phase 2 is blocked on the user raising their
      GitHub Actions spending limit and unverified code shouldn't sit on `main`. `ee7217d`

## Phase 2: Live verification
**Blocked — see the PAUSED note above.** Real, external side effects (creates public/private
GitHub repos) — proceeds directly per explicit user decision, disposable timestamped repo
names, deleted after.
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
