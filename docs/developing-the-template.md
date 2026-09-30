# Developing the template

`src/main/g8/`'s files are giter8 templates, not plain Scala/SQL — they're full of
`$domain_name$`/`$package$` substitutions, `$domain_name;format="cap"$` capitalized-identifier
placeholders, and (in the field-codegen-relevant files) `codegen:fields:<TAG>` anchor comments.
No IDE or compiler understands that syntax, so you can't type-check, run, or test the template
directly. There isn't a tool that makes this feel like editing ordinary code — that's inherent
to giter8 templating, not specific to this project.

## The workflow

1. **Generate a real, compiler-checked scratch instance:**

   ```
   ./scripts/dev-regenerate.sh [--domain-name <name>] [--package <package>] [--field-spec <path>]
   ```

   Defaults `--domain-name` to `devcheck` if omitted. Produces two directories from the same
   generation, both under the gitignored `.dev/`:
   - `.dev/<domain-name>-service` — yours to edit.
   - `.dev/<domain-name>-service.baseline` — an untouched copy, frozen right after generation
     (and, if `--field-spec` was given, after the field-spec was applied) but *before* you've
     made any hand-edits.

   Both are wiped and regenerated fresh every run. The script also runs `sbt scalafmt` (so the
   baseline is already correctly formatted — see the "identifier-length line-wrapping" note in
   the main README) and `sbt test`, confirming you're starting from a green state.

2. **Edit and test the generated copy** (`.dev/<domain-name>-service`) like normal software —
   full IDE support, real compiler errors, `sbt test` — until your change works.

3. **See exactly what you changed:**

   ```
   ./scripts/dev-diff.sh [--domain-name <name>]
   ```

   Diffs the `.baseline` copy against your edited copy (`diff -ru`, printed to the terminal).
   Because the baseline was frozen *after* formatting/field-spec application, the diff shows
   only your actual edits — no formatting noise.

4. **Port the diff back into `src/main/g8/` by hand**, reinstating whatever the generated text
   came from:
   - The domain name / capitalized domain name → `$domain_name$` / `$domain_name;format="cap"$`.
   - The package → `$package$`.
   - Any literal `$` in the result (Scala string interpolation, Skunk's `$`-based SQL params) →
     escaped as `\$`, or generation will abort — see the main README's known giter8 gotchas.
   - If your change touches a field-codegen insertion point, the corresponding
     `codegen:fields:<TAG>` anchor comment — not a literal value. Grep `src/main/g8/` for
     `codegen:fields:` to see the existing anchors and how they're placed; the join-style rules
     that render each one live in `tools/codegen/src/main/scala/codegen/AnchorTransformer.scala`.

   `dev-diff.sh` deliberately does **not** try to automate this step. The diff is against
   *instantiated* text (`widget`, `widgetservice`, `Widget`), and reverse-mapping that back onto
   template placeholders is inherently ambiguous — the string `"widget"` you typed by hand as an
   unrelated literal is textually indistinguishable from `"widget"` that came from
   `$domain_name$` substitution. An automated guess that gets this wrong would silently corrupt
   the template in a way that might only surface later, when someone generates with a
   *different* domain name. This step needs a human making the actual template-authoring
   judgment call.

5. **Regenerate fresh and confirm** the templated version reproduces the change correctly — not
   just that your scratch copy worked:

   ```
   ./scripts/dev-regenerate.sh --domain-name <name> [--field-spec <path>]
   ```

   If it still compiles and passes, the templating captured your change correctly.

## Small, non-structural changes

For genuinely mechanical edits (comment wording, whitespace, non-behavioral tweaks) that don't
touch anything placeholder- or anchor-sensitive, editing `src/main/g8/` directly without a
round-trip through a scratch instance is fine — the workflow above is for anything where you
want real compiler/test feedback before committing to how the template should read.

## Related docs

- [`README.md`](../README.md) — generating a service, the known giter8 gotchas
  (`format="cap"` vs `format="Cap"`, `\$` escaping).
- [`tools/codegen/README.md`](../tools/codegen/README.md) — the field-spec format, relevant if
  your change touches a field-codegen insertion point.
