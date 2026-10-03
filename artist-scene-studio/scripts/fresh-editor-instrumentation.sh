#!/usr/bin/env bash
set -euo pipefail
APP_ID=studio.artistscene.app
API_LEVEL="${API_LEVEL:-36}"
API_TAG="api${API_LEVEL}"
TEST_SUITE="${TEST_SUITE:-editor}"
case "$TEST_SUITE" in
  editor) TEST_CLASSES="studio.artistscene.app.RendererLaunchTest"; EXPECTED_TESTS=1 ;;
  humanoid) TEST_CLASSES="studio.artistscene.app.HumanoidAppearanceTest"; EXPECTED_TESTS=1 ;;
  bicycle) TEST_CLASSES="studio.artistscene.app.MechanicalActorsTest#bicyclePartsPersist"; EXPECTED_TESTS=1 ;;
  car) TEST_CLASSES="studio.artistscene.app.MechanicalActorsTest#carPartsPersist"; EXPECTED_TESTS=1 ;;
  tree) TEST_CLASSES="studio.artistscene.app.MechanicalActorsTest#treeRenders"; EXPECTED_TESTS=1 ;;
  *) echo "Unknown test suite: $TEST_SUITE" >&2; exit 1 ;;
esac
TEST_LOG="artist-scene-studio-${API_TAG}-instrumentation-run.log"
fail() { echo "ERROR: $*" >&2; exit 1; }
adb_bounded() { timeout 30s adb "$@"; }
cleanup() {
  # Retain the last rendered frames even when a later assertion fails.
  timeout 15s adb pull "/sdcard/Android/data/$APP_ID/files/" "artist-scene-studio-${API_TAG}-instrumentation-device-files" >/dev/null 2>&1 || true
  timeout 10s adb logcat -d -v threadtime > "artist-scene-studio-${API_TAG}-instrumentation-logcat.txt" || true
  timeout 10s adb logcat -b crash -d > "artist-scene-studio-${API_TAG}-instrumentation-crashes.txt" || true
}
trap cleanup EXIT
adb_bounded install -r -t app/build/outputs/apk/debug/app-debug.apk
adb_bounded install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb_bounded shell pm clear "$APP_ID"
INSTRUMENTATION_LOG="artist-scene-studio-${API_TAG}-instrumentation.txt"
timeout 420s adb shell am instrument -w -r -e class "$TEST_CLASSES" \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" >"$INSTRUMENTATION_LOG" 2>&1 || {
  cat "$INSTRUMENTATION_LOG" | tee -a "$TEST_LOG"
  fail "Android instrumentation command failed"
}
cat "$INSTRUMENTATION_LOG" | tee -a "$TEST_LOG"
python3 - "$INSTRUMENTATION_LOG" "$EXPECTED_TESTS" <<'PYINSTRUMENTATION' || fail "Android instrumentation did not pass the complete suite"
import re, sys
text = open(sys.argv[1]).read()
result = re.search(r"OK \((\d+) tests?\)", text)
assert result and int(result.group(1)) == int(sys.argv[2]), "Missing successful test-suite summary"
assert re.search(r"INSTRUMENTATION_CODE:\s*-1\b", text), "Missing successful runner completion"
assert not re.search(r"INSTRUMENTATION_STATUS_CODE:\s*-(?:1|2|3|4)\b", text), "Failed or skipped test"
assert "FAILURES!!!" not in text and "INSTRUMENTATION_FAILED" not in text
print("Complete Android instrumentation suite passed")
PYINSTRUMENTATION
adb_bounded get-state | grep -qx device || fail "Emulator disconnected after instrumentation"
# Fetch all proof in one transfer while the emulator is still running.
PROOF_DIR="artist-scene-studio-${API_TAG}-instrumentation-device-files"
adb_bounded pull "/sdcard/Android/data/$APP_ID/files/" "$PROOF_DIR" >/dev/null 2>&1 || fail "Instrumentation proof transfer failed"
if [ "$TEST_SUITE" != editor ] && [ "$TEST_SUITE" != humanoid ]; then
  if [ "$TEST_SUITE" = tree ]; then stages=tree; else stages="$TEST_SUITE-before $TEST_SUITE-after $TEST_SUITE-saved"; fi
  for stage in $stages; do
    cp "$PROOF_DIR/mechanical-${stage}.png" "artist-scene-studio-${API_TAG}-mechanical-${stage}.png" || fail "Mechanical $stage screenshot was missing"
    python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-mechanical-${stage}.png" || fail "Mechanical $stage viewport was black"
  done
  echo "Fresh API $API_LEVEL mechanical actors passed with all screenshot proof"
  exit 0
fi
if [ "$TEST_SUITE" = editor ]; then
cp "$PROOF_DIR/instrumented-viewport.png" "artist-scene-studio-${API_TAG}-instrumented.png" || fail "Instrumentation screenshot was missing"
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-instrumented.png" || fail "Instrumentation viewport was black"
else
for stage in before hair-short hair-afro hair-bob appearance posed; do
  cp "$PROOF_DIR/humanoid-${stage}.png" "artist-scene-studio-${API_TAG}-humanoid-${stage}.png" || fail "Humanoid $stage screenshot was missing"
  python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-humanoid-${stage}.png" || fail "Humanoid $stage viewport was black"
done
fi

echo "Fresh API $API_LEVEL instrumentation passed with all screenshot proof"
