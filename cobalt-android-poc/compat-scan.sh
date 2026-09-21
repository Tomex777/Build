#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-../cobalt-upstream}"
OUT="${2:-compat-report.txt}"

if [[ ! -d "$ROOT" ]]; then
  echo "Cobalt source directory not found: $ROOT" >&2
  exit 2
fi

count() {
  local label="$1"
  local pattern="$2"
  local n
  n=$(rg -n --glob '*.java' "$pattern" "$ROOT/modules" 2>/dev/null | wc -l | tr -d ' ')
  printf '%-34s %s\n' "$label" "$n"
}

{
  echo "Cobalt -> Android compatibility scan"
  echo "===================================="
  echo
  count "Virtual-thread references" 'Thread\.ofVirtual\('
  count "Foreign-memory API references" 'java\.lang\.foreign'
  count "JDK Vector API references" 'jdk\.incubator\.vector'
  count "Java 21 List getFirst/getLast" '\.(getFirst|getLast)\(\)'
  count "Math.clamp references" 'Math\.clamp\('
  echo
  echo "Interpretation:"
  echo "- Linked/Web protocol code is reusable."
  echo "- Virtual threads must be replaced for Android."
  echo "- Foreign-memory/FFmpeg/call paths should be excluded from the Android core."
  echo "- Vector code can use Cobalt's scalar fallback."
  echo "- Newer collection/Math APIs need compatibility rewrites where used by the linked path."
} | tee "$OUT"
