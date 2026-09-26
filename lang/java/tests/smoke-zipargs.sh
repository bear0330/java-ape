#!/bin/sh
# Verify the Java APE consumes embedded /zip/.args (cosmofy-style, no updater).
set -eu
[ "$#" -eq 1 ] || { echo "usage: $0 java.com" >&2; exit 2; }
RUNTIME=$(realpath "$1")
ROOT=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
REPO=$(CDPATH= cd -- "$ROOT/../../.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/java-zipargs.XXXXXX")
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

test -f "$ROOT/hello.jar" || { echo "missing $ROOT/hello.jar" >&2; exit 1; }
cp "$RUNTIME" "$TMP/test.com"
chmod +x "$TMP/test.com"
printf '%s\n' '-jar' '/zip/hello.jar' '...' > "$TMP/.args"
cp "$ROOT/hello.jar" "$TMP/hello.jar"
(cd "$TMP" && zip -q test.com .args hello.jar)

OUT=$(env -u COSMOPOLITAN_DISABLE_ZIPOS "$TMP/test.com")
case "$OUT" in
  *"hello from java.com"*) ;;
  *)
    printf 'zipargs -jar smoke failed: got <%s>\n' "$OUT" >&2
    exit 1
    ;;
esac

# Optional argv merge: compile EchoArgs if a host javac exists.
JAVAC=
for c in \
  "$REPO/build-tools/openjdk25-boot/bin/javac" \
  "${BASELOC:-}/build-tools/openjdk25-boot/bin/javac" \
  "${JAVA_HOME:-}/bin/javac"
do
  if [ -n "$c" ] && [ -x "$c" ]; then
    JAVAC=$c
    break
  fi
done
if [ -z "$JAVAC" ] && command -v javac >/dev/null 2>&1; then
  JAVAC=$(command -v javac)
fi

if [ -n "$JAVAC" ]; then
  "$JAVAC" -d "$TMP" "$ROOT/EchoArgs.java"
  printf '%s\n' '-cp' '/zip' 'EchoArgs' '...' > "$TMP/.args"
  (cd "$TMP" && zip -q test.com .args EchoArgs.class)
  ACTUAL=$(env -u COSMOPOLITAN_DISABLE_ZIPOS "$TMP/test.com" --help 'two words')
  if [ "$ACTUAL" != '--help|two words' ]; then
    printf 'zipargs argv-merge smoke failed: got <%s>\n' "$ACTUAL" >&2
    exit 1
  fi
  printf 'native .args smoke passed (jar + argv merge)\n'
else
  printf 'native .args smoke passed (jar; skip argv merge, no javac)\n'
fi
