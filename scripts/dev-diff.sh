#!/usr/bin/env bash
set -euo pipefail

# Shows exactly what you've changed in a scripts/dev-regenerate.sh scratch
# instance, by diffing it against its untouched .baseline sibling (frozen at
# generation time, before any hand-edits). Plain output only - this does NOT
# patch src/main/g8/ for you: reverse-mapping instantiated text back onto
# $domain_name$/$package$/codegen:fields: placeholders is inherently
# ambiguous (the same generated text can come from a placeholder or from
# something you typed by hand) and risks silently corrupting the template.
# Use the diff to manually port your change back, with judgment, into the
# right anchor points. See docs/developing-the-template.md.
#
# Usage:
#   ./scripts/dev-diff.sh [--domain-name <name>]

usage() {
  cat <<'USAGE'
Usage: dev-diff.sh [options]

Options:
  --domain-name <name>   Same domain name you passed to dev-regenerate.sh.
                          Defaults to "devcheck".
  -h, --help              Show this help and exit.
USAGE
}

DOMAIN_NAME="devcheck"

while [ $# -gt 0 ]; do
  case "$1" in
    --domain-name)
      DOMAIN_NAME="$2"
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

if [[ ! "$DOMAIN_NAME" =~ ^[a-z][a-z0-9]*$ ]]; then
  echo "Error: --domain-name must be lowercase alphanumeric, starting with a letter" \
    "(got: $DOMAIN_NAME)." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GEN_DIR="$ROOT_DIR/.dev/${DOMAIN_NAME}-service"
BASELINE_DIR="$ROOT_DIR/.dev/${DOMAIN_NAME}-service.baseline"

if [ ! -d "$BASELINE_DIR" ] || [ ! -d "$GEN_DIR" ]; then
  echo "Error: missing $GEN_DIR or $BASELINE_DIR - run dev-regenerate.sh first" \
    "(with --domain-name $DOMAIN_NAME if not the default)." >&2
  exit 1
fi

diff -ru -x target "$BASELINE_DIR" "$GEN_DIR" || true
