#!/bin/sh
# Synchronizes the Java APE-owned recipe, documentation, and tests from a
# superconfigure checkout.  Build inputs stay under lang/java; public docs and
# test fixtures deliberately live outside that directory in this repository.
set -eu

usage() {
  echo "Usage: $0 [--force] /path/to/superconfigure" >&2
  exit 2
}

FORCE=false
case "${1:-}" in
  --force) FORCE=true; shift ;;
esac
[ "$#" -eq 1 ] || usage

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE=$(CDPATH= cd -- "$1" && pwd)
for path in lang/java/BUILD.mk docs/java-host-services.md tests/java/validate.sh; do
  [ -e "$SOURCE/$path" ] || {
    echo "missing required source path: $SOURCE/$path" >&2
    exit 1
  }
done

if [ "$FORCE" != true ] && git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  for path in lang/java docs/java-host-services.md tests/java; do
    if [ -n "$(git -C "$ROOT" status --porcelain --untracked-files=all -- "$path")" ]; then
      echo "refusing to overwrite modified $path; commit/stash it or rerun with --force" >&2
      exit 1
    fi
  done
fi

STAGE=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-sync-stage.XXXXXX")
BACKUP=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-sync-backup.XXXXXX")
cleanup() { rm -rf "$STAGE"; }
trap cleanup EXIT HUP INT TERM

mkdir -p "$STAGE/lang" "$STAGE/docs" "$STAGE/tests"
cp -pR "$SOURCE/lang/java" "$STAGE/lang/java"
cp -p "$SOURCE/docs/java-host-services.md" "$STAGE/docs/java-host-services.md"
cp -pR "$SOURCE/tests/java" "$STAGE/tests/java"
find "$STAGE/tests/java" -type f \( -name '*.pyc' -o -name '*.pyo' \) -delete
find "$STAGE/tests/java" -type d -name __pycache__ -empty -delete

sync_path() {
  relative=$1
  destination=$ROOT/$relative
  staged=$STAGE/$relative
  backup=$BACKUP/$relative
  mkdir -p "$(dirname "$destination")" "$(dirname "$backup")"
  if [ -e "$destination" ]; then mv "$destination" "$backup"; fi
  mv "$staged" "$destination"
}

sync_path lang/java
sync_path docs/java-host-services.md
sync_path tests/java

echo "Synchronized from $SOURCE"
echo "Replaced files are backed up at $BACKUP"
