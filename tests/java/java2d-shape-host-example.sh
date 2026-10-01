#!/bin/sh
# Runs a stateful OpenJDK ShapeSpanIterator replacement through Python.
set -eu

: "${BASELOC:?BASELOC is required}"
SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/java2d-host-example-lib.sh"

JAVA=${1:-$BASELOC/results/bin/java.com}
PYTHON=${PYTHON:-python3}
BOOT=${BOOT_JDK:-$BASELOC/build-tools/openjdk25-boot}
MODULES=${JAVA_APE_MODULES:-$BASELOC/results/libexec/java-modules.zip}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-java2d-shape-example.XXXXXX")
SERVER_PID=
trap 'java2d_cleanup "$?" "$SERVER_PID" "$WORK"' EXIT HUP INT TERM

java2d_require_dependencies \
  "$JAVA" \
  "$MODULES" \
  "$PYTHON" \
  'import pylsp_jsonrpc' \
  "install the example dependency: $PYTHON -m pip install python-lsp-jsonrpc==1.1.2"

java2d_prepare_ape "$JAVA" "$MODULES" "$WORK"
java2d_compile_example "$BOOT" "$WORK" "$BASELOC/tests/java/Java2DShapeHostExample.java"

PORT=$(java2d_loopback_port "$PYTHON")
READY="$WORK/ready"
PYTHONDONTWRITEBYTECODE=1 "$PYTHON" "$BASELOC/tests/java/java2d-shape-host-example.py" \
  --port "$PORT" \
  --ready "$READY" &
SERVER_PID=$!

java2d_wait_for_ready_file "$READY"

APE_HOST="127.0.0.1:$PORT" \
APE_HOST_TOKEN=java2d-shape-example-secret \
APE_VIRTUAL_LIBRARIES=awt \
  "$WORK/java2d.com" \
  --add-exports=java.desktop/sun.java2d.pipe=ALL-UNNAMED \
  --add-opens=java.desktop/sun.java2d.pipe=ALL-UNNAMED \
  -cp "$WORK" \
  Java2DShapeHostExample

wait "$SERVER_PID"
SERVER_PID=
