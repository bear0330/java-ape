#!/bin/sh
# Exercises ProcessBuilder pipes, exit status, working directory, and environment.
set -eu

: "${BASELOC:?BASELOC is required}"
JAVA=${1:-${JAVA_APE_TEST_JAVA:-$BASELOC/results/bin/java.com}}
BOOT=${BOOT_JDK:-$BASELOC/build-tools/openjdk25-boot}
JAVAC=${JAVAC:-$BOOT/bin/javac}
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-process-builder.XXXXXX")

cleanup() {
  status=$?
  rm -rf "$WORK"
  exit "$status"
}
trap cleanup EXIT HUP INT TERM

test -x "$JAVA" || { echo "missing Java APE: $JAVA" >&2; exit 1; }
test -x "$JAVAC" || { echo "missing javac: $JAVAC" >&2; exit 1; }

"$JAVAC" --release 17 -d "$WORK" "$ROOT/ProcessBuilderTest.java"
if [ "${JAVA_APE_PROCESS_TEST_WINDOWS:-0}" = 1 ]; then
  "$JAVA" -cp "$WORK" ProcessBuilderTest --windows
else
  "$JAVA" -cp "$WORK" ProcessBuilderTest
fi
