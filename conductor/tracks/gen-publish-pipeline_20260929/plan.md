# Plan: Automated generation → repo-creation → push → CI pipeline

## Phase 1: CI workflow template
Infrastructure, not Red/Green — a static template file, verified by generation + local checks.
- [ ] Task: Add `.github/workflows/ci.yml` to `src/main/g8/` — `actions/checkout`, `actions/setup-java`
      (temurin 21, matching the packaging base image), `sbt/setup-sbt`, then
      `sbt scalafmtCheck Test/scalafmtCheck test` on push to the default branch.
      `GITHUB_ACTOR: ${{ github.actor }}`, `GITHUB_TOKEN: ${{ secrets.GH_PACKAGES_TOKEN }}` (a
      custom repo secret, since the automatic same-repo-scoped `GITHUB_TOKEN` can't resolve
      another repo's GitHub Packages).
- [ ] Task: Regenerate a `widget` service via giter8, confirm the workflow file is present and
      valid YAML, and `sbt scalafmtCheck test` still passes locally (proves the new file doesn't
      interfere with the build).
- [ ] Task: Conductor - User Manual Verification 'Phase 1: CI workflow template' (Protocol in
      workflow.md)

## Phase 2: Build the orchestration script (local steps only, no GitHub calls yet)
- [ ] Task: `scripts/generate-and-publish-service.sh` — argument parsing (`--domain-name`
      required, `--package`/`--field-spec`/`--repo-name` optional, defaulting `--repo-name` to
      `<domain_name>-service`), `--help` text, and a clear error on a missing required arg.
      Verify by invoking with `--help` and with no args.
- [ ] Task: Implement the **generate** step (reusing the `giter8.LauncherMain` pattern from
      `scripts/verify-g8-template.sh`) and the optional **field-codegen** step (reusing the
      pattern from `scripts/verify-codegen-tool.sh`) into a local working directory. Verify
      locally: run with and without `--field-spec`, confirm the generated directory is correct
      in both cases — no GitHub calls in this task.
- [ ] Task: Implement **repo creation** (`gh repo create <owner>/<repo-name> --public`, aborting
      cleanly if the name already exists), **push** (git init/commit/push), and **CI secret**
      (`gh secret set GH_PACKAGES_TOKEN`, reusing the caller's own `gh auth token`, set *before*
      the push so the first CI run can resolve packages).
- [ ] Task: Implement **wait-for-CI** (poll `gh run watch` or equivalent) and clear pass/fail
      reporting.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Build the orchestration script'
      (Protocol in workflow.md)

## Phase 3: Live end-to-end verification
This phase has real, external side effects (creates public GitHub repos, uses your `gh`
credentials) — proceeds directly per explicit user decision, using disposable timestamped repo
names, cleaned up afterward.
- [ ] Task: Run the script for real with a disposable domain name; confirm: repo created
      (public), pushed, `GH_PACKAGES_TOKEN` secret set, and the triggered GitHub Actions run
      passes.
- [ ] Task: Run again with `--field-spec` against a second disposable domain name; confirm CI
      still passes with the extra fields.
- [ ] Task: Run with a `--repo-name` that already exists; confirm a clean abort with no changes
      to the existing repo.
- [ ] Task: Delete the throwaway repos created during this phase's verification
      (`gh repo delete`).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Live end-to-end verification'
      (Protocol in workflow.md)

## Phase 4: Documentation
- [ ] Task: Document the pipeline in the README — usage, required `gh auth` scopes, the
      `GH_PACKAGES_TOKEN` secret and why it's needed, `--field-spec`/`--repo-name`, and the
      public-by-default visibility.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Documentation' (Protocol in
      workflow.md)
