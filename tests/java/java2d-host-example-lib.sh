#!/bin/sh
# Shared setup for executable Java2D Host Services examples.

java2d_cleanup() {
  status=$1
  server_pid=$2
  work=$3

  if [ -n "$server_pid" ]; then
    kill "$server_pid" 2>/dev/null || true
  fi

  rm -rf "$work"
  exit "$status"
}

java2d_require_dependencies() {
  ape=$1
  modules=$2
  python=$3

  test -x "$ape" || {
    echo "missing Java APE: $ape" >&2
    exit 1
  }

  test -f "$modules" || {
    echo "missing Java module repository: $modules" >&2
    exit 1
  }

  "$python" -c "$4" 2>/dev/null || {
    echo "$5" >&2
    exit 1
  }
}

java2d_prepare_ape() {
  ape=$1
  modules=$2
  work=$3

  cp "$ape" "$work/java2d.com"
  mkdir "$work/modules"
  unzip -q "$modules" \
    'modules/java.desktop/*' \
    'modules/java.datatransfer/*' \
    -d "$work/modules"
  (
    cd "$work/modules"
    zip -q -r "$work/java2d.com" modules
  )
  chmod +x "$work/java2d.com"
}

java2d_compile_example() {
  boot=$1
  work=$2
  source_file=$3

  "$boot/bin/javac" --release 17 -d "$work" "$source_file"
}

java2d_loopback_port() {
  "$1" - <<'PY'
import socket

sock = socket.socket()
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()
PY
}

java2d_wait_for_ready_file() {
  ready=$1
  tries=0

  while [ ! -f "$ready" ] && [ "$tries" -lt 50 ]; do
    tries=$((tries + 1))
    sleep 0.1
  done

  test -f "$ready" || {
    echo "Java2D host did not start" >&2
    exit 1
  }
}
