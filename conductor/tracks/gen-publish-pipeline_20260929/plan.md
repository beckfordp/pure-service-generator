# Plan: Automated generation → repo-creation → push → CI pipeline

## Phase 1: CI workflow template
Infrastructure, not Red/Green — a static template file, verified by generation + local checks.
- [x] Task: Add `.github/workflows/ci.yml` to `src/main/g8/` — `actions/checkout`, `actions/setup-java`
      (temurin 21, matching the packaging base image), `sbt/setup-sbt`, then
      `sbt scalafmtCheck Test/scalafmtCheck test` on push to the default branch.
      `GITHUB_ACTOR: ${{ github.actor }}`, `GITHUB_TOKEN: ${{ secrets.GH_PACKAGES_TOKEN }}` (a
      custom repo secret, since the automatic same-repo-scoped `GITHUB_TOKEN` can't resolve
      another repo's GitHub Packages). `624dae6`
- [x] Task: Regenerate a `widget` service via giter8, confirm the workflow file is present and
      valid YAML, and `sbt scalafmtCheck test` still passes locally (proves the new file doesn't
      interfere with the build). `624dae6`
- [x] Task: Conductor - User Manual Verification 'Phase 1: CI workflow template' (Protocol in
      workflow.md) - verified directly (prompting off): regenerated widget-service, `ci.yml`
      present with correctly unescaped `${{ ... }}` expressions, `python3 -c "yaml.safe_load"`
      confirms valid YAML, `sbt scalafmtCheck test` 52/52 passed locally.

## Phase 2: Build the orchestration script (local steps only, no GitHub calls yet)
- [x] Task: `scripts/generate-and-publish-service.sh` — argument parsing (`--domain-name`
      required, `--package`/`--field-spec`/`--repo-name` optional, defaulting `--repo-name` to
      `<domain_name>-service`), `--help` text, and a clear error on a missing required arg.
      Verify by invoking with `--help` and with no args. `27bf4a6`
- [x] Task: Implement the **generate** step (reusing the `giter8.LauncherMain` pattern from
      `scripts/verify-g8-template.sh`) and the optional **field-codegen** step (reusing the
      pattern from `scripts/verify-codegen-tool.sh`) into a local working directory. Verify
      locally: run with and without `--field-spec`, confirm the generated directory is correct
      in both cases — no GitHub calls in this task. `ff4a857`
- [x] Task: Implement **repo creation** (`gh repo create <owner>/<repo-name> --public`, aborting
      cleanly if the name already exists), **push** (git init/commit/push), and **CI secret**
      (`gh secret set GH_PACKAGES_TOKEN`, reusing the caller's own `gh auth token`, set *before*
      the push so the first CI run can resolve packages). `42ad7ae`
- [x] Task: Implement **wait-for-CI** (poll `gh run watch` or equivalent) and clear pass/fail
      reporting. `c405983`
- [x] Task: Conductor - User Manual Verification 'Phase 2: Build the orchestration script'
      (Protocol in workflow.md) - verified directly (prompting off): `bash -n` syntax check
      passes; `--help`/missing-arg/invalid-field-spec-path all behave correctly; generate (with
      and without `--field-spec`) succeeds locally; the collision-abort path was proven for real
      against this repo's own already-existing name (`--repo-name pure-service-generator`), with
      no side effects. Repo-creation/push/CI-wait are inherently unprovable without a real
      GitHub Actions run — proven live in Phase 3.

## Phase 3: Live end-to-end verification
This phase has real, external side effects (creates public GitHub repos, uses your `gh`
credentials) — proceeds directly per explicit user decision, using disposable timestamped repo
names, cleaned up afterward.
- [x] Task: Run the script for real with a disposable domain name; confirm: repo created
      (public), pushed, `GH_PACKAGES_TOKEN` secret set, and the triggered GitHub Actions run
      passes. `dd6f41a`

      First attempt (`pipelinecheck1790706669`) surfaced a real bug — CI's `scalafmtCheck`
      failed because the script never ran the documented `sbt scalafmt` reformat pass before
      committing. Fixed (see `dd6f41a`), throwaway repo deleted, re-run
      (`pipelinecheck1790706825-service`) passed CI in 1m27s:
      https://github.com/beckfordp/pipelinecheck1790706825-service/actions/runs/36612993355
- [x] Task: Run again with `--field-spec` against a second disposable domain name; confirm CI
      still passes with the extra fields.

      `fspec1790706958-service`, CI passed in 1m23s (confirmed via `gh run list`).
- [x] Task: Run with a `--repo-name` that already exists; confirm a clean abort with no changes
      to the existing repo.

      Re-ran against the just-created `fspec1790706958-service` name — aborted cleanly at the
      existence check, before any git/gh mutation.
- [x] Task: Delete the throwaway repos created during this phase's verification
      (`gh repo delete`).

      Both `pipelinecheck1790706825-service` and `fspec1790706958-service` deleted; confirmed
      via `gh repo list` that neither remains.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Live end-to-end verification'
      (Protocol in workflow.md) - verified directly (prompting off) via the three real runs
      above; all three GitHub side effects (repo creation, secret, push, CI) and the collision
      guard behaved exactly as specified.

## Phase 4: Documentation
- [x] Task: Document the pipeline in the README — usage, required `gh auth` scopes, the
      `GH_PACKAGES_TOKEN` secret and why it's needed, `--field-spec`/`--repo-name`, and the
      public-by-default visibility. `1cb648f`
- [x] Task: Conductor - User Manual Verification 'Phase 4: Documentation' (Protocol in
      workflow.md) - verified directly (prompting off): new README section covers usage,
      prerequisites/scopes, the `GH_PACKAGES_TOKEN` rationale, and visibility default.

## Phase: Review Fixes
- [x] Task: Apply review suggestions `1afb527`
