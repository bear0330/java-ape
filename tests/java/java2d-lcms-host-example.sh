#!/bin/sh
# Runs a real OpenJDK LCMS Java2D replacement through Pillow ImageCms.
set -eu

: "${BASELOC:?BASELOC is required}"
SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/java2d-host-example-lib.sh"

JAVA=${1:-$BASELOC/results/bin/java.com}
PYTHON=${PYTHON:-python3}
BOOT=${BOOT_JDK:-$BASELOC/build-tools/openjdk25-boot}
MODULES=${JAVA_APE_MODULES:-$BASELOC/results/libexec/java-modules.zip}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-java2d-lcms-example.XXXXXX")
SERVER_PID=
trap 'java2d_cleanup "$?" "$SERVER_PID" "$WORK"' EXIT HUP INT TERM

java2d_require_dependencies \
  "$JAVA" \
  "$MODULES" \
  "$PYTHON" \
  'import PIL, pylsp_jsonrpc' \
  "install the example dependencies: $PYTHON -m pip install Pillow==11.3.0 python-lsp-jsonrpc==1.1.2"

java2d_prepare_ape "$JAVA" "$MODULES" "$WORK"
java2d_compile_example "$BOOT" "$WORK" "$BASELOC/tests/java/Java2DLcmsHostExample.java"

PORT=$(java2d_loopback_port "$PYTHON")
READY="$WORK/ready"
PYTHONDONTWRITEBYTECODE=1 "$PYTHON" "$BASELOC/tests/java/java2d-lcms-host-example.py" \
  --port "$PORT" \
  --ready "$READY" &
SERVER_PID=$!

java2d_wait_for_ready_file "$READY"

APE_HOST="127.0.0.1:$PORT" \
APE_HOST_TOKEN=java2d-lcms-example-secret \
APE_VIRTUAL_LIBRARIES=awt,lcms \
  "$WORK/java2d.com" -cp "$WORK" Java2DLcmsHostExample

wait "$SERVER_PID"
SERVER_PID=
