#!/usr/bin/env bash
# Run instrumentation and export page captures in ONE shell.
# android-emulator-runner executes each `script:` line with a separate
# `sh -c`, so multiline loops and shell variables must live in this script.
set -u -o pipefail

api="${1:?Usage: ci-verify-emulator.sh <api-level>}"
app_id="com.nice.juicewrld"
mkdir -p screenshots

test_status=0
gradle -p juice-wrld-android --no-daemon :app:connectedDebugAndroidTest || test_status=$?

missing=0
for page in home love sad favorites song-sheet; do
  out="screenshots/${page}-api-${api}.png"
  if ! adb exec-out run-as "$app_id" cat "files/screenshots/${page}.png" > "$out"; then
    echo "::error::Unable to export Android ${api} screenshot: ${page}"
    rm -f "$out"
    missing=$((missing + 1))
  elif [[ ! -s "$out" ]]; then
    echo "::error::Exported Android ${api} screenshot is empty: ${page}"
    rm -f "$out"
    missing=$((missing + 1))
  else
    echo "Captured ${out} ($(wc -c < "$out") bytes)"
  fi
done

adb logcat -d -v brief -t 1200 > "screenshots/logcat-api-${api}.txt" || true

if (( test_status != 0 )); then
  echo "::error::Android ${api} instrumentation failed (Gradle exit ${test_status})"
  exit "$test_status"
fi
if (( missing != 0 )); then
  echo "::error::Android ${api} missing ${missing}/5 expected screenshots"
  exit 21
fi
echo "Android ${api}: all instrumentation tests and all 5 visual captures passed."
