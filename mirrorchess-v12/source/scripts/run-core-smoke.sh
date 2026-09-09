#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="${TMPDIR:-/tmp}/mirror-chess-core-smoke.jar"
kotlinc \
  "$ROOT/app/src/main/java/com/night/mirrorchess/chess/ChessCore.kt" \
  "$ROOT/app/src/main/java/com/night/mirrorchess/chess/Pgn.kt" \
  "$ROOT/app/src/main/java/com/night/mirrorchess/ai/MovePredictor.kt" \
  "$ROOT/app/src/main/java/com/night/mirrorchess/ai/Maia3Encoding.kt" \
  "$ROOT/app/src/main/java/com/night/mirrorchess/mirror/MirrorProfile.kt" \
  "$ROOT/tools/CoreSmoke.kt" \
  -include-runtime -d "$OUT"
java -jar "$OUT"
