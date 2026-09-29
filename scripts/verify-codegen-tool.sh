#!/usr/bin/env bash
set -euo pipefail

# Manual verification for field-codegen_20260929 Phase 5: this track's
# acceptance-criteria proof. Generates a real 'widget' service from this
# repo's giter8 template (src/main/g8/), applies a sample field-spec (all
# four supported types) via the tools/codegen tool, then runs
# scalafmtCheck + the full test suite on the result - proving the codegen
# tool's anchor-based rewrites produce a service that still builds, is
# correctly formatted, and passes its (now field-extended) tests, including
# the full CRUD lifecycle assertions on the new fields.
#
# Requires GITHUB_ACTOR/GITHUB_TOKEN (a PAT with read:packages scope) in the
# environment, to resolve purerestlib from GitHub Packages in the generated
# project.
#
# Usage: ./scripts/verify-codegen-tool.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEGEN_DIR="$ROOT_DIR/tools/codegen"
cd "$ROOT_DIR"

FAILED=0
WORK_DIR="$(mktemp -d -t verify-codegen-tool)"

cleanup() {
  echo
  echo "Cleaning up..."
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

if [ -z "${GITHUB_ACTOR:-}" ] || [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "FAIL: GITHUB_ACTOR/GITHUB_TOKEN must be set to resolve purerestlib from GitHub Packages." >&2
  exit 1
fi

# A throwaway sbt project used only to run giter8's launcher library -
# project-local, not a global plugin, so nothing about the caller's sbt
# environment changes.
LAUNCHER_DIR="$WORK_DIR/g8-launcher"
mkdir -p "$LAUNCHER_DIR/project"
echo "sbt.version=1.13.0" >"$LAUNCHER_DIR/project/build.properties"
echo 'libraryDependencies += "org.foundweekends.giter8" %% "giter8-launcher" % "0.18.0"' \
  >"$LAUNCHER_DIR/build.sbt"

echo "1. Generating a 'widget' service from this repo's template..."
GEN_DIR="$WORK_DIR/widget-service"
if (cd "$LAUNCHER_DIR" && sbt -batch "runMain giter8.LauncherMain file://${ROOT_DIR} --domain_name=widget -o ${GEN_DIR}") \
  >"$WORK_DIR/generate.log" 2>&1; then
  echo "   OK: generated at $GEN_DIR"
else
  echo "   FAIL: generation failed" >&2
  tail -60 "$WORK_DIR/generate.log" >&2
  exit 1
fi

echo
echo "2. Writing a sample field-spec (all four supported types)..."
FIELD_SPEC="$WORK_DIR/field-spec.yaml"
cat >"$FIELD_SPEC" <<'YAML'
fields:
  - name: color
    type: String
    example: "red"
  - name: weight
    type: Int
    example: "7"
  - name: fragile
    type: Boolean
    example: "true"
  - name: expiresAt
    type: Instant
    example: "2026-06-01T00:00:00Z"
YAML
echo "   OK: wrote $FIELD_SPEC"

echo
echo "3. Running the field-codegen tool against the generated service..."
if (cd "$CODEGEN_DIR" && sbt -batch "runMain codegen.Main ${GEN_DIR} ${FIELD_SPEC}") \
  >"$WORK_DIR/codegen.log" 2>&1; then
  applied="$(grep -oE 'applied field spec to [0-9]+ file\(s\)' "$WORK_DIR/codegen.log" | tail -1)"
  echo "   OK: ${applied:-codegen tool ran}"
else
  echo "   FAIL: field-codegen tool failed" >&2
  tail -80 "$WORK_DIR/codegen.log" >&2
  exit 1
fi

echo
echo "4. Confirming no anchor markers remain unresolved..."
leftover="$(grep -rln --fixed-strings 'codegen:fields:' "$GEN_DIR" 2>/dev/null || true)"
if [ -n "$leftover" ]; then
  echo "   FAIL: unresolved anchor markers remain in:" >&2
  echo "$leftover" >&2
  FAILED=1
else
  echo "   OK: no unresolved anchor markers"
fi

echo
echo "5. sbt scalafmt (one reformat pass is expected) then scalafmtCheck + test..."
if (cd "$GEN_DIR" && sbt -batch scalafmt Test/scalafmt scalafmtCheck Test/scalafmtCheck test) \
  >"$WORK_DIR/test.log" 2>&1; then
  passed="$(grep -oE 'Passed: Total [0-9]+' "$WORK_DIR/test.log" | tail -1)"
  echo "   OK: scalafmtCheck + test passed (${passed:-unknown count})"
else
  echo "   FAIL: scalafmtCheck/test failed" >&2
  tail -100 "$WORK_DIR/test.log" >&2
  FAILED=1
fi

echo
echo "6. Spot-checking the new fields actually made it into the domain entity..."
ENTITY_FILE="$GEN_DIR/src/main/scala/widgetservice/WidgetStore.scala"
if grep -q "color: String" "$ENTITY_FILE" \
  && grep -q "weight: Int" "$ENTITY_FILE" \
  && grep -q "fragile: Boolean" "$ENTITY_FILE" \
  && grep -q "expiresAt: java.time.Instant" "$ENTITY_FILE"; then
  echo "   OK: all four new fields present on the Widget entity"
else
  echo "   FAIL: one or more new fields missing from $ENTITY_FILE" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
