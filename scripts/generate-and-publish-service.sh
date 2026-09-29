#!/usr/bin/env bash
set -euo pipefail

# Generates a new service from this repo's giter8 template, optionally
# extends it with extra domain fields via tools/codegen, creates a public
# GitHub repo under the caller's account, pushes the service, wires up
# GitHub Actions CI credentials, and waits for that first CI run to go
# green - this project's answer to service-generator's Jenkins pipeline.
#
# Usage:
#   ./scripts/generate-and-publish-service.sh --domain-name <name> \
#     [--package <package>] [--field-spec <path>] [--repo-name <name>]
#
# Requires: gh (authenticated), sbt, Docker (for the generated service's
# Postgres-backed tests).

usage() {
  cat <<'USAGE'
Usage: generate-and-publish-service.sh --domain-name <name> [options]

Required:
  --domain-name <name>   Lowercase, singular domain name (e.g. "widget").

Options:
  --package <package>    Scala package for the generated service.
                          Defaults to giter8's own default ($domain_name$service).
  --field-spec <path>    Path to a field-spec YAML file (see tools/codegen's README
                          section) to apply via the field-codegen tool before pushing.
  --repo-name <name>     GitHub repo name. Defaults to "<domain-name>-service".
  -h, --help             Show this help and exit.
USAGE
}

DOMAIN_NAME=""
PACKAGE=""
FIELD_SPEC=""
REPO_NAME=""

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
    --repo-name)
      REPO_NAME="$2"
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

if [ -z "$DOMAIN_NAME" ]; then
  echo "Error: --domain-name is required." >&2
  usage >&2
  exit 1
fi

if [ -n "$FIELD_SPEC" ] && [ ! -f "$FIELD_SPEC" ]; then
  echo "Error: --field-spec file not found: $FIELD_SPEC" >&2
  exit 1
fi

if [ -z "$REPO_NAME" ]; then
  REPO_NAME="${DOMAIN_NAME}-service"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Domain name: $DOMAIN_NAME"
echo "Package:     ${PACKAGE:-<default>}"
echo "Field spec:  ${FIELD_SPEC:-<none>}"
echo "Repo name:   $REPO_NAME"

echo
echo "(generate/publish steps not yet implemented)"
