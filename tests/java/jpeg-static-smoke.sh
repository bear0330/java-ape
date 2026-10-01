#!/bin/sh
set -eu

JAVA=$1
ROOT=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
JAVAC=${JAVAC:-"$ROOT/../../build-tools/openjdk25-boot/bin/javac"}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/javajpeg-static.XXXXXX")

cleanup() {
  rm -rf "$WORK"
}
trap cleanup EXIT INT TERM

test -x "$JAVAC" || {
  echo "missing boot javac: $JAVAC" >&2
  exit 1
}

"$JAVAC" -d "$WORK" "$ROOT/JpegStaticLibraryTest.java"
"$JAVA" --enable-native-access=ALL-UNNAMED -cp "$WORK" JpegStaticLibraryTest
