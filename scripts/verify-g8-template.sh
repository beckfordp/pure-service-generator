#!/usr/bin/env bash
set -euo pipefail

# Manual verification for g8-template_20260929 Phase 4: this track's
# acceptance-criteria proof. Generates a real service from this repo's giter8
# template (src/main/g8/) via giter8's own launcher library - sbt's built-in
# `new` command only resolves a hardcoded list of GitHub template shortcuts in
# this sbt version, not arbitrary file:// URIs (see README's "Generating a new
# service from this template") - then runs its test suite, checks for leftover
# un-templated references, spot-checks the independent `package` override, and
# confirms this repo's own reference service still builds/tests standalone.
#
# Requires GITHUB_ACTOR/GITHUB_TOKEN (a PAT with read:packages scope) in the
# environment, to resolve purerestlib from GitHub Packages in the generated
# project.
#
# Usage: ./scripts/verify-g8-template.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0
WORK_DIR="$(mktemp -d -t verify-g8-template)"

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

generate() {
  # generate <output-dir> <extra-args...>
  local out="$1"
  shift
  (cd "$LAUNCHER_DIR" && sbt -batch "runMain giter8.LauncherMain file://${ROOT_DIR} --domain_name=widget $* -o ${out}") \
    >"$WORK_DIR/generate.log" 2>&1
}

echo "1. Generating a 'widget' service from this repo's template..."
GEN_DIR="$WORK_DIR/widget-service"
if generate "$GEN_DIR"; then
  echo "   OK: generated at $GEN_DIR"
else
  echo "   FAIL: generation failed" >&2
  tail -60 "$WORK_DIR/generate.log" >&2
  exit 1
fi

echo
echo "2. Checking for leftover un-templated order/Order/orderservice references..."
hits="$(grep -rn --fixed-strings -e "orderservice" -e "OrderStore" -e "OrderRoutes" -e "order-service" \
  "$GEN_DIR" 2>/dev/null || true)"
if [ -n "$hits" ]; then
  echo "   FAIL: found leftover references:" >&2
  echo "$hits" >&2
  FAILED=1
else
  echo "   OK: no leftover order-domain references"
fi

echo
echo "3. sbt scalafmt (one reformat pass is expected) then scalafmtCheck + test..."
if (cd "$GEN_DIR" && sbt -batch scalafmt Test/scalafmt scalafmtCheck Test/scalafmtCheck test) \
  >"$WORK_DIR/test.log" 2>&1; then
  passed="$(grep -oE 'Passed: Total [0-9]+' "$WORK_DIR/test.log" | tail -1)"
  echo "   OK: scalafmtCheck + test passed (${passed:-unknown count})"
else
  echo "   FAIL: scalafmtCheck/test failed" >&2
  tail -80 "$WORK_DIR/test.log" >&2
  FAILED=1
fi

echo
echo "4. Spot-checking an independent 'package' override compiles..."
PKG_DIR="$WORK_DIR/widget-service-pkg"
if generate "$PKG_DIR" "--package=com.example.widgetservice"; then
  if [ -d "$PKG_DIR/src/main/scala/com/example/widgetservice" ] \
    && (cd "$PKG_DIR" && sbt -batch compile) >"$WORK_DIR/pkg-compile.log" 2>&1; then
    echo "   OK: com/example/widgetservice generated and compiles"
  else
    echo "   FAIL: package override did not generate/compile correctly" >&2
    tail -40 "$WORK_DIR/pkg-compile.log" >&2 || true
    FAILED=1
  fi
else
  echo "   FAIL: generation with --package override failed" >&2
  FAILED=1
fi

echo
echo "5. Confirming this repo's own reference service still builds/tests standalone..."
if (cd "$ROOT_DIR" && sbt -batch scalafmtCheck Test/scalafmtCheck test) >"$WORK_DIR/source-test.log" 2>&1; then
  echo "   OK: source repo's own suite still passes"
else
  echo "   FAIL: source repo's own suite regressed" >&2
  tail -60 "$WORK_DIR/source-test.log" >&2
  FAILED=1
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "One or more checks FAILED. See above." >&2
  exit 1
fi
