#!/bin/sh
set -eu

[ "$#" -le 1 ] || { echo "Usage: $0 [superconfigure-directory]" >&2; exit 2; }
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
value() { sed -n "s/^$1=//p" "$ROOT/superconfigure.lock"; }
REPOSITORY=$(value superconfigure_repository)
REF=$(value superconfigure_ref)
EXPECTED=$(value superconfigure_commit)
if [ "$#" -eq 0 ]; then
  SUPER=$ROOT/superconfigure
  if [ ! -e "$SUPER" ]; then git clone --depth 1 --branch "$REF" "$REPOSITORY" "$SUPER"; fi
else
  SUPER=$1
fi
SUPER=$(CDPATH= cd -- "$SUPER" && pwd)
ACTUAL=$(git -C "$SUPER" rev-parse HEAD 2>/dev/null || true)
[ "$ACTUAL" = "$EXPECTED" ] || { echo "expected superconfigure $EXPECTED, got ${ACTUAL:-not-a-matching-git-checkout}" >&2; exit 1; }
# Do not provision Cosmopolitan here.  The base project's setup and cosmo
# scripts clone its current source and generate the matching cosmocc tree.
# The Java recipe downloads and verifies its own OpenJDK and boot-JDK inputs.
mkdir -p "$SUPER/.ape-overlay-backups"
BACKUPS=$(mktemp -d "$SUPER/.ape-overlay-backups/java-ape.XXXXXX")
DESTINATION=$SUPER/lang/java
if [ -e "$DESTINATION" ]; then mkdir -p "$BACKUPS/lang"; mv "$DESTINATION" "$BACKUPS/lang/java"; fi
mkdir -p "$SUPER/lang"
cp -a "$ROOT/lang/java" "$DESTINATION"
grep -Fqx 'include lang/java/BUILD.mk' "$SUPER/lang/BUILD.mk" || printf '\ninclude lang/java/BUILD.mk\n' >> "$SUPER/lang/BUILD.mk"

# The locked base's BSD-sed parser, Cosmocc's Linux-ELF dedupe helper, and
# macOS make 3.81 require narrowly scoped Darwin-only framework patches.
if [ "$(uname -s)" = Darwin ]; then
  CHECK_SHA=$SUPER/config/check_sha.sh
  if grep -Fq '\s' "$CHECK_SHA"; then
    mkdir -p "$BACKUPS/config"
    cp -p "$CHECK_SHA" "$BACKUPS/config/check_sha.sh"
    patch --forward -d "$SUPER" -p1 < "$ROOT/scripts/darwin-check-sha.patch"
  fi
  COSMO_SCRIPT=$SUPER/.github/scripts/cosmo
  if ! grep -Fq 'Java APE overlay: skip ELF dedupe on Darwin' "$COSMO_SCRIPT"; then
    mkdir -p "$BACKUPS/.github/scripts"
    cp -p "$COSMO_SCRIPT" "$BACKUPS/.github/scripts/cosmo"
    patch --forward -d "$SUPER" -p1 < "$ROOT/scripts/darwin-cosmo.patch"
  fi
fi
printf 'Installed Java overlay into %s. Backup: %s\n' "$SUPER" "$BACKUPS"
