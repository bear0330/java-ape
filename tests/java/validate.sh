#!/bin/sh
set -eu
JAVA=${1:-${RESULTS:+$RESULTS/bin/java.com}}
JAVA=${JAVA:-./results/bin/java.com}
ROOT=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
test -x "$JAVA" || { echo "missing java APE: $JAVA" >&2; exit 1; }

echo "==> $JAVA -version"
"$JAVA" -version
echo "==> $JAVA --list-modules"
"$JAVA" --list-modules
echo "==> $JAVA -jar hello.jar"
"$JAVA" -jar "$ROOT/hello.jar"
echo "==> zip .args"
/bin/sh "$ROOT/smoke-zipargs.sh" "$JAVA"

HTTP_PID=
cleanup() {
  if [ -n "$HTTP_PID" ]; then
    kill "$HTTP_PID" 2>/dev/null || true
    wait "$HTTP_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM
python3 -m http.server 18080 --bind 127.0.0.1 --directory "$ROOT/http-root" >/dev/null 2>&1 &
HTTP_PID=$!
sleep 0.3

echo "==> $JAVA -cp tests/java Smoke"
"$JAVA" -cp "$ROOT" Smoke http://127.0.0.1:18080/
