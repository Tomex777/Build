#!/usr/bin/env bash
set -euo pipefail
APP_ID=studio.artistscene.app
API_LEVEL="${API_LEVEL:-36}"
API_TAG="api${API_LEVEL}"
TEST_LOG="artist-scene-studio-${API_TAG}-instrumentation-run.log"
fail() { echo "ERROR: $*" >&2; exit 1; }
adb_bounded() { timeout 30s adb "$@"; }
cleanup() {
  timeout 10s adb logcat -d -v threadtime > "artist-scene-studio-${API_TAG}-instrumentation-logcat.txt" || true
  timeout 10s adb logcat -b crash -d > "artist-scene-studio-${API_TAG}-instrumentation-crashes.txt" || true
}
trap cleanup EXIT
adb_bounded install -r -t app/build/outputs/apk/debug/app-debug.apk
adb_bounded install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb_bounded shell pm clear "$APP_ID"
INSTRUMENTATION_LOG="artist-scene-studio-${API_TAG}-instrumentation.txt"
timeout 300s adb shell am instrument -w -r \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" >"$INSTRUMENTATION_LOG" 2>&1 || {
  cat "$INSTRUMENTATION_LOG" | tee -a "$TEST_LOG"
  fail "Android instrumentation command failed"
}
cat "$INSTRUMENTATION_LOG" | tee -a "$TEST_LOG"
python3 - "$INSTRUMENTATION_LOG" <<'PYINSTRUMENTATION' || fail "Android instrumentation did not pass the complete suite"
import re, sys
text = open(sys.argv[1]).read()
result = re.search(r"OK \((\d+) tests?\)", text)
assert result and int(result.group(1)) >= 2, "Missing successful test-suite summary"
assert re.search(r"INSTRUMENTATION_CODE:\s*-1\b", text), "Missing successful runner completion"
assert not re.search(r"INSTRUMENTATION_STATUS_CODE:\s*-(?:1|2|3|4)\b", text), "Failed or skipped test"
assert "FAILURES!!!" not in text and "INSTRUMENTATION_FAILED" not in text
print("Complete Android instrumentation suite passed")
PYINSTRUMENTATION
adb_bounded get-state | grep -qx device || fail "Emulator disconnected after instrumentation"
adb_bounded pull /sdcard/Android/data/$APP_ID/files/instrumented-viewport.png "artist-scene-studio-${API_TAG}-instrumented.png" >/dev/null 2>&1 || fail "Instrumentation screenshot was missing"
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-instrumented.png" || fail "Instrumentation viewport was black"
for stage in before hair-short hair-afro hair-bob appearance posed; do
  adb_bounded pull "/sdcard/Android/data/$APP_ID/files/humanoid-${stage}.png" "artist-scene-studio-${API_TAG}-humanoid-${stage}.png" >/dev/null 2>&1 || fail "Humanoid $stage screenshot was missing"
  python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-humanoid-${stage}.png" || fail "Humanoid $stage viewport was black"
done


echo "Fresh API $API_LEVEL instrumentation passed with all screenshot proof"

