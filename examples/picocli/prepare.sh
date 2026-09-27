#!/bin/sh
set -eu

EXAMPLE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$EXAMPLE/../.." && pwd)
BUILD="$EXAMPLE/build"
JAVAC=${JAVACOSMOFY_JAVAC:-"$ROOT/../superconfigure/build-tools/openjdk25-boot/bin/javac"}
JAR=${JAVACOSMOFY_JAR:-"$(dirname -- "$JAVAC")/jar"}
PICOCLI="$BUILD/picocli-4.7.7.jar"
SHA256=f86e30fffd10d2b13b8caa8d4b237a7ee61f2ffccf5b1941de718b765d235bf8
URL=https://repo1.maven.org/maven2/info/picocli/picocli/4.7.7/picocli-4.7.7.jar
WORK=$(mktemp -d "${TMPDIR:-/tmp}/javacosmofy-picocli.XXXXXX")
trap 'rm -rf "$WORK"' EXIT HUP INT TERM

test -x "$JAVAC" || { echo "missing javac: $JAVAC" >&2; exit 1; }
mkdir -p "$BUILD/classes"
curl -fL --retry 3 -o "$PICOCLI" "$URL"
actual=$(sha256sum "$PICOCLI" | awk '{print $1}')
[ "$actual" = "$SHA256" ] || { echo 'Picocli SHA-256 mismatch' >&2; exit 1; }
"$JAVAC" -cp "$PICOCLI" -d "$BUILD/classes" "$EXAMPLE/src/demo/Checksum.java"
unzip -q "$PICOCLI" -d "$WORK/fat"
cp -a "$BUILD/classes/." "$WORK/fat/"
"$JAR" --create --file "$BUILD/checksum.jar" --main-class demo.Checksum -C "$WORK/fat" .
printf 'Prepared: %s\n' "$BUILD/checksum.jar"
