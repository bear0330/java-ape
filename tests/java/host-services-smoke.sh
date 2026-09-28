#!/bin/sh
# Runs both Host Services levels against a local framed-protocol Python server.
set -eu

: "${BASELOC:?BASELOC is required}"
JAVA=${1:-${JAVA_APE_TEST_JAVA:-$BASELOC/results/bin/java.com}}
BOOT=${BOOT_JDK:-$BASELOC/build-tools/openjdk25-boot}
JAVAC=${JAVAC:-$BOOT/bin/javac}
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/java-ape-host-smoke.XXXXXX")
READY="$WORK/ready"
SERVER_PID=

cleanup() {
  status=$?
  if [ -n "$SERVER_PID" ]; then kill "$SERVER_PID" 2>/dev/null || true; fi
  rm -rf "$WORK"
  exit "$status"
}
trap cleanup EXIT HUP INT TERM

test -x "$JAVA" || { echo "missing Java APE: $JAVA" >&2; exit 1; }
test -x "$JAVAC" || { echo "missing javac: $JAVAC" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }

BASELOC="$BASELOC" "$BASELOC/lang/java/build-host-shim" "$WORK/host-services.jar"
"$JAVAC" --release 17 -cp "$WORK/host-services.jar" -d "$WORK/classes" \
  "$ROOT/HostNativeTest.java" \
  "$ROOT/HostShimTest.java" \
  "$ROOT/VirtualLibraryTest.java"

# The direct fallback must remain disabled unless both host variables exist.
if env -u JAVA_APE_HOST -u JAVA_APE_HOST_TOKEN "$JAVA" -cp "$WORK/classes" HostNativeTest 2>&1 | grep -q 'UnsatisfiedLinkError'; then :; else
  echo "host-native fallback was unexpectedly enabled without configuration" >&2
  exit 1
fi

PORT=$(python3 - <<'PY'
import socket
sock = socket.socket()
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()
PY
)
python3 "$ROOT/host-services-test-server.py" --port "$PORT" --ready "$READY" &
SERVER_PID=$!
tries=0
while [ ! -f "$READY" ] && [ "$tries" -lt 50 ]; do
  tries=$((tries + 1))
  sleep 0.1
done
test -f "$READY" || { echo "host test server did not start" >&2; exit 1; }

export JAVA_APE_HOST=http://127.0.0.1:$PORT
export JAVA_APE_HOST_TOKEN=random-secret
if env -u JAVA_APE_VIRTUAL_LIBRARIES "$JAVA" -cp "$WORK/classes" VirtualLibraryTest 2>&1 | grep -q 'UnsatisfiedLinkError'; then :; else
  echo "virtual native library was unexpectedly enabled without configuration" >&2
  exit 1
fi
if JAVA_APE_VIRTUAL_LIBRARIES=not-awt "$JAVA" -cp "$WORK/classes" VirtualLibraryTest 2>&1 | grep -q 'UnsatisfiedLinkError'; then :; else
  echo "virtual native library allowlist matched the wrong name" >&2
  exit 1
fi
JAVA_APE_VIRTUAL_LIBRARIES=awt "$JAVA" -cp "$WORK/classes" VirtualLibraryTest
"$JAVA" -cp "$WORK/classes" HostNativeTest
# Exercise HotSpot's compiled native wrapper as well as the interpreter path.
"$JAVA" -Xcomp -cp "$WORK/classes" HostNativeTest
"$JAVA" -cp "$WORK/classes" HostShimTest
if JAVA_APE_HOST_TOKEN=wrong "$JAVA" -cp "$WORK/classes" HostShimTest 2>&1 | grep -q 'SecurityException'; then :; else
  echo "host token rejection did not map to SecurityException" >&2
  exit 1
fi
echo "Java APE Host Services smoke test passed"
