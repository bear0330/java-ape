#!/bin/sh
# Runs a Host -> JVM -> Host nested native-call test on one connection.
set -eu

: "${BASELOC:?BASELOC is required}"
SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/java2d-host-example-lib.sh"

JAVA=${1:-$BASELOC/results/bin/java.com}
PYTHON=${PYTHON:-python3}
BOOT=${BOOT_JDK:-$BASELOC/build-tools/openjdk25-boot}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-host-reentrant.XXXXXX")
SERVER_PID=
trap 'java2d_cleanup "$?" "$SERVER_PID" "$WORK"' EXIT HUP INT TERM

test -x "$JAVA" || { echo "missing Java APE: $JAVA" >&2; exit 1; }
test -x "$BOOT/bin/javac" || { echo "missing javac: $BOOT/bin/javac" >&2; exit 1; }
"$PYTHON" -c 'import pylsp_jsonrpc' 2>/dev/null || {
  echo "install the test dependency: $PYTHON -m pip install python-lsp-jsonrpc==1.1.2" >&2
  exit 1
}

"$BOOT/bin/javac" --release 17 -d "$WORK" "$BASELOC/tests/java/HostReentrantTest.java"

PORT=$(java2d_loopback_port "$PYTHON")
READY="$WORK/ready"
PYTHONDONTWRITEBYTECODE=1 "$PYTHON" "$BASELOC/tests/java/host-services-reentrant.py" \
  --port "$PORT" \
  --ready "$READY" &
SERVER_PID=$!

java2d_wait_for_ready_file "$READY"

APE_HOST="127.0.0.1:$PORT" \
APE_HOST_TOKEN=reentrant-host-secret \
  "$JAVA" -cp "$WORK" HostReentrantTest

wait "$SERVER_PID"
SERVER_PID=
