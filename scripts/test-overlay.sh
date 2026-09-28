#!/bin/sh
# Runs Java APE's external test suite against an installed superconfigure tree.
set -eu

[ "$#" -le 1 ] || { echo "Usage: $0 [superconfigure-directory]" >&2; exit 2; }
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SUPER=${1:-$ROOT/superconfigure}
SUPER=$(CDPATH= cd -- "$SUPER" && pwd)
JAVA=$SUPER/results/bin/java.com

"$ROOT/tests/java/validate.sh" "$JAVA"
BASELOC=$SUPER "$ROOT/tests/java/host-services-smoke.sh" "$JAVA"
