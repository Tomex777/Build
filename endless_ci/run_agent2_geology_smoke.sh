#!/usr/bin/env bash
# CPU-only independent Agent 2 verification. This does not replace Android
# compilation, API26/API36 installation, GLES screenshots or frame benchmarks.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/endless_m2_override/app/src/main/java/com/night/endless/engine/render"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
if ! command -v kotlinc >/dev/null 2>&1; then
    echo "kotlinc is required to run Agent 2 CPU smoke checks" >&2
    exit 2
fi
kotlinc \
    "$SRC/SurfaceMicroMaterial.kt" \
    "$SRC/PlanetaryGrainTreatment.kt" \
    "$SRC/WorldGeomorphology.kt" \
    "$ROOT/endless_ci/agent2_smoke/GeologySmoke.kt" \
    -include-runtime -d "$TMP/geology-smoke.jar"
java -jar "$TMP/geology-smoke.jar"
