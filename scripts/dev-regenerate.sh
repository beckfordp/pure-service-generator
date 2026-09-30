#!/usr/bin/env bash
set -euo pipefail

# Generates a scratch instance of this repo's giter8 template into a local,
# gitignored directory - something real and compiler-checked to develop
# against when working on the template itself, since src/main/g8/'s
# placeholder-laden files can't be type-checked or run directly. See
# docs/developing-the-template.md for the full workflow.
#
# Produces two directories from the same generation:
#   .dev/<domain-name>-service           - yours to edit
#   .dev/<domain-name>-service.baseline  - untouched, frozen at generation
#                                           time (before any --field-spec is
#                                           applied) - diff against it with
#                                           scripts/dev-diff.sh once you've
#                                           made changes.
# Both are wiped and regenerated fresh on every run.
#
# Usage:
#   ./scripts/dev-regenerate.sh [--domain-name <name>] [--package <package>] \
#     [--field-spec <path>]
#
# Requires: sbt, Docker (for the generated service's Postgres-backed tests).

usage() {
  cat <<'USAGE'
Usage: dev-regenerate.sh [options]

Options:
  --domain-name <name>   Lowercase, singular domain name. Defaults to "devcheck".
  --package <package>    Scala package for the generated service.
                          Defaults to giter8's own default ($domain_name$service).
  --field-spec <path>    Path to a field-spec YAML file, applied via the
                          field-codegen tool to the editable copy only (the
                          .baseline copy never sees it).
  -h, --help             Show this help and exit.
USAGE
}

DOMAIN_NAME="devcheck"
PACKAGE=""
FIELD_SPEC=""

while [ $# -gt 0 ]; do
  case "$1" in
    --domain-name)
      DOMAIN_NAME="$2"
      shift 2
      ;;
    --package)
      PACKAGE="$2"
      shift 2
      ;;
    --field-spec)
      FIELD_SPEC="$2"
      shift 2
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ -n "$FIELD_SPEC" ]; then
  if [ ! -f "$FIELD_SPEC" ]; then
    echo "Error: --field-spec file not found: $FIELD_SPEC" >&2
    exit 1
  fi
  # Resolve to an absolute path before any `cd` below.
  FIELD_SPEC="$(cd "$(dirname "$FIELD_SPEC")" && pwd)/$(basename "$FIELD_SPEC")"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEGEN_DIR="$ROOT_DIR/tools/codegen"
DEV_DIR="$ROOT_DIR/.dev"
GEN_DIR="$DEV_DIR/${DOMAIN_NAME}-service"
BASELINE_DIR="$DEV_DIR/${DOMAIN_NAME}-service.baseline"

echo "Domain name: $DOMAIN_NAME"
echo "Package:     ${PACKAGE:-<default>}"
echo "Field spec:  ${FIELD_SPEC:-<none>}"
echo "Output:      $GEN_DIR"

rm -rf "$GEN_DIR" "$BASELINE_DIR"
mkdir -p "$DEV_DIR"

WORK_DIR="$(mktemp -d -t dev-regenerate)"
cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

# A throwaway sbt project used only to run giter8's launcher library -
# project-local, not a global plugin, so nothing about the caller's sbt
# environment changes. Same pattern as scripts/generate-and-publish-service.sh.
LAUNCHER_DIR="$WORK_DIR/g8-launcher"
mkdir -p "$LAUNCHER_DIR/project"
echo "sbt.version=1.13.0" >"$LAUNCHER_DIR/project/build.properties"
echo 'libraryDependencies += "org.foundweekends.giter8" %% "giter8-launcher" % "0.18.0"' \
  >"$LAUNCHER_DIR/build.sbt"

echo
echo "1. Generating '$DOMAIN_NAME' from this repo's template..."
GEN_ARGS=(--domain_name="$DOMAIN_NAME")
if [ -n "$PACKAGE" ]; then
  GEN_ARGS+=(--package="$PACKAGE")
fi
(cd "$LAUNCHER_DIR" && sbt -batch "runMain giter8.LauncherMain file://${ROOT_DIR} ${GEN_ARGS[*]} -o ${GEN_DIR}") \
  >"$WORK_DIR/generate.log" 2>&1 || {
  echo "   FAIL: generation failed" >&2
  tail -60 "$WORK_DIR/generate.log" >&2
  exit 1
}
echo "   OK: generated at $GEN_DIR"

STEP=2
if [ -n "$FIELD_SPEC" ]; then
  echo
  echo "$STEP. Applying field-spec via the field-codegen tool..."
  (cd "$CODEGEN_DIR" && sbt -batch "runMain codegen.Main ${GEN_DIR} ${FIELD_SPEC}") \
    >"$WORK_DIR/codegen.log" 2>&1 || {
    echo "   FAIL: field-codegen tool failed" >&2
    tail -80 "$WORK_DIR/codegen.log" >&2
    exit 1
  }
  echo "   OK: field spec applied"
  STEP=$((STEP + 1))
fi

echo
echo "$STEP. sbt scalafmt (reformats identifier-length line-wrapping before it becomes your"
echo "   editing baseline, so dev-diff.sh only ever shows *your* changes)..."
(cd "$GEN_DIR" && sbt -batch scalafmt Test/scalafmt) >"$WORK_DIR/scalafmt.log" 2>&1 || {
  echo "   FAIL: sbt scalafmt failed" >&2
  tail -60 "$WORK_DIR/scalafmt.log" >&2
  exit 1
}
echo "   OK: reformatted"
STEP=$((STEP + 1))

echo
echo "$STEP. Freezing an untouched .baseline copy for later diffing (post-format, pre-edit)..."
cp -R "$GEN_DIR" "$BASELINE_DIR"
echo "   OK: $BASELINE_DIR"
STEP=$((STEP + 1))

echo
echo "$STEP. sbt test..."
(cd "$GEN_DIR" && sbt -batch test) >"$WORK_DIR/test.log" 2>&1 || {
  echo "   FAIL: test failed" >&2
  tail -100 "$WORK_DIR/test.log" >&2
  exit 1
}
passed="$(grep -oE 'Passed: Total [0-9]+' "$WORK_DIR/test.log" | tail -1)"
echo "   OK: ${passed:-tests passed}"

echo
echo "Done: $GEN_DIR"
echo "Baseline for diffing: $BASELINE_DIR"
echo "Edit the former, then run: ./scripts/dev-diff.sh --domain-name $DOMAIN_NAME"
