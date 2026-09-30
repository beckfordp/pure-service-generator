# clean-scripts

## 1.0 PURPOSE

Most files under `scripts/` in this repo are one-off Conductor Phase-completion verification
scripts (`scripts/verify-*.sh`), created because this project runs with `/prompt off` — each
phase's manual-verification step becomes a committed, real script instead of an interactive
walkthrough (see the `prompt` skill for the full convention). Once a track finishes, that
script has usually done its one job and has no lasting purpose — it isn't referenced from
anywhere and nobody will run it again.

The exception is scripts the README actually tells users to run themselves (currently
`generate-and-publish-service.sh`) — those are real, ongoing tooling and must stay.

This skill removes the former and keeps the latter, without needing to re-derive the rule
each time `scripts/` gets cluttered again.

## 2.0 PROTOCOL

1. **Resolve `scripts/`:** repo-root-relative. If it doesn't exist, announce "No scripts/
   directory found." and stop.

2. **Find README references:** Read the repo's `README.md`. Extract every `scripts/<name>`
   path mentioned anywhere in it (a plain text search for `scripts/` is sufficient — these are
   always written as literal relative paths, not indirected).

3. **Classify every file directly under `scripts/`:**
   - **Keep** — referenced anywhere in `README.md`.
   - **Delete** — not referenced in `README.md`.

4. **Confirm before touching anything:** Use the `AskUserQuestion` tool to show both groups
   (Keep / Delete) and ask for a go-ahead (do not repeat the full list again in the chat):
   - **header:** "Confirm"
   - **question:** List the files in each group, then ask: "Proceed with deleting the
     'Delete' group?"
   - **options:**
       - Label: "Yes", Description: "Delete the listed scripts."
       - Label: "No", Description: "Don't delete anything."
   If the user says no, stop here — do not delete anything, do not ask again.

5. **Tag the pre-deletion state:** before removing anything, create a lightweight tag at the
   current `HEAD` named `redundant-scripts-<YYYY-MM-DD>` (today's date, so repeated runs over
   time don't collide with each other): `git tag redundant-scripts-<YYYY-MM-DD>`. This gives a
   memorable, permanent name to retrieve any deleted script by later — no SHA-hunting needed:
   `git checkout redundant-scripts-<YYYY-MM-DD> -- scripts/<name>` restores one directly, and
   `git show redundant-scripts-<YYYY-MM-DD>:scripts/<name>` prints one without restoring it.

6. **Delete (git-tracked, not archived):** `git rm scripts/<name>` for each file in the
   Delete group. The tag from step 5 (plus ordinary git history) already preserves them, so
   there's no need to move them to an archive directory first.

7. **Commit:** Stage and commit with a message summarizing what was removed, e.g.:
   `chore(scripts): Remove one-off verification scripts superseded by README-referenced ones`
   Mention the tag name from step 5 in the commit body so it's discoverable from `git log` too.

8. **Announce:** Summarize what was kept and what was removed, and give the exact tag name and
   retrieval command (`git checkout <tag> -- scripts/<name>`) so the user doesn't have to
   remember the syntax later.

## 3.0 NOTES

- Only `README.md` counts as "keep" justification. If a script is referenced somewhere else
  (another doc, CI config, another script) but not the README, this skill still marks it for
  deletion — flag that case explicitly when presenting the Keep/Delete groups so the user can
  override it, rather than silently trusting the README-only heuristic.
- This skill only touches files directly under `scripts/` in the current repo — it doesn't
  recurse into subdirectories or touch anything outside `scripts/`.
