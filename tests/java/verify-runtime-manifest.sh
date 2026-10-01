#!/bin/sh
# Verify that a Java module repository is paired with this exact runtime.
set -eu

RUNTIME=${1:?runtime required}
MODULES=${2:?module repository required}

test -f "$RUNTIME" || {
  echo "missing Java runtime: $RUNTIME" >&2
  exit 1
}

test -f "$MODULES" || {
  echo "missing Java module repository: $MODULES" >&2
  exit 1
}

python3 - "$RUNTIME" "$MODULES" <<'PY'
import hashlib
import json
import sys
import zipfile

runtime_path, modules_path = sys.argv[1:]

digest = hashlib.sha256()
with open(runtime_path, "rb") as runtime:
    for chunk in iter(lambda: runtime.read(1024 * 1024), b""):
        digest.update(chunk)
runtime_sha256 = digest.hexdigest()

with zipfile.ZipFile(modules_path) as modules:
    manifest = json.loads(
        modules.read(".java-ape/runtime-manifest.json").decode("utf-8")
    )

if manifest.get("format") != 1:
    raise SystemExit(f"unsupported runtime manifest: {manifest}")

expected = manifest.get("runtime", {}).get("sha256")
if expected != runtime_sha256:
    raise SystemExit(
        f"runtime SHA-256 mismatch: expected {expected}, got {runtime_sha256}"
    )

print("Java runtime/module repository pairing passed")
PY
