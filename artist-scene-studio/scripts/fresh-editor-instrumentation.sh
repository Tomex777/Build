#!/usr/bin/env bash
set -euo pipefail
APP_ID=studio.artistscene.app
API_LEVEL="${API_LEVEL:-36}"
API_TAG="api${API_LEVEL}"
TEST_SUITE="${TEST_SUITE:-editor}"
case "$TEST_SUITE" in
  editor) TEST_CLASSES="studio.artistscene.app.RendererLaunchTest"; EXPECTED_TESTS=1 ;;
  humanoid) TEST_CLASSES="studio.artistscene.app.HumanoidAppearanceTest"; EXPECTED_TESTS=1 ;;
  anime) TEST_CLASSES="studio.artistscene.app.AnimePoseStudyInstrumentedTest"; EXPECTED_TESTS=1 ;;
  bicycle) TEST_CLASSES="studio.artistscene.app.MechanicalActorsTest#bicyclePartsPersist"; EXPECTED_TESTS=1 ;;
  car) TEST_CLASSES="studio.artistscene.app.MechanicalActorsTest#carPartsPersist"; EXPECTED_TESTS=1 ;;
  tree) TEST_CLASSES="studio.artistscene.app.MechanicalActorsTest#treeRenders"; EXPECTED_TESTS=1 ;;
  *) echo "Unknown test suite: $TEST_SUITE" >&2; exit 1 ;;
esac
TEST_LOG="artist-scene-studio-${API_TAG}-instrumentation-run.log"
fail() { echo "ERROR: $*" >&2; exit 1; }
# A fresh API 26 emulator may not service package-manager commands until well after the
# runner's boot-completed signal. Keep each ADB operation bounded, but allow startup slack.
adb_bounded() {
  echo "ADB: $1 (timeout 150s)"
  timeout 150s adb "$@"
}
cleanup() {
  # Stop only our dedicated proof watcher. Never stop a global ADB service.
  if [ -n "${proof_watch_pid:-}" ]; then
    kill "$proof_watch_pid" >/dev/null 2>&1 || true
    wait "$proof_watch_pid" >/dev/null 2>&1 || true
  fi
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
# The Android 16 emulator sometimes stops AFTER tests pass but BEFORE the final
# adb pull. Preserve genuine device screenshots as soon as the test writes them.
# These are raw UiAutomator captures; no mockups or generated images.
PROOF_DIR="artist-scene-studio-${API_TAG}-instrumentation-device-files"
REMOTE_DIR="/sdcard/Android/data/$APP_ID/files"
if [ "$TEST_SUITE" = anime ]; then
  mkdir -p "$PROOF_DIR"
  (
    while true; do
      for stage in posed neutral female-neutral female-hair-still female-hair-gravity; do
        target="$PROOF_DIR/anime-$stage.png"
        if [ ! -s "$target" ]; then
          partial="$target.pending"
          if timeout 12s adb pull "$REMOTE_DIR/anime-$stage.png" "$partial" >/dev/null 2>&1 && [ -s "$partial" ]; then
            mv "$partial" "$target"
          else
            rm -f "$partial"
          fi
        fi
      done
      for filename in anime-inspector-hair-controls.png anime-inspector-hair-controls.xml; do
        target="$PROOF_DIR/$filename"
        if [ ! -s "$target" ]; then
          pending="$target.pending"
          if timeout 12s adb pull "$REMOTE_DIR/$filename" "$pending" >/dev/null 2>&1 &&
              [ -s "$pending" ]; then
            mv "$pending" "$target"
          else
            rm -f "$pending"
          fi
        fi
      done
      sleep 6
    done
  ) &
  proof_watch_pid=$!
fi
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
# Copy only the screenshots required for acceptance. Pulling the entire app-private files
# directory caused a 30-second ADB transfer timeout (and loss of evidence) on API 36.
# Mandatory frames remain mandatory: missing or corrupt proof still fails the job.
PROOF_DIR="artist-scene-studio-${API_TAG}-instrumentation-device-files"
REMOTE_DIR="/sdcard/Android/data/$APP_ID/files"
mkdir -p "$PROOF_DIR"
pull_proof() {
  local filename="$1"
  local target="$PROOF_DIR/$filename"
  echo "Retrieving acceptance proof: $filename"
  if [ ! -s "$target" ]; then
    timeout 120s adb pull "$REMOTE_DIR/$filename" "$target" ||
      fail "Could not retrieve $filename from API $API_LEVEL emulator"
  fi
  test -s "$target" || fail "Empty proof file: $filename"
}
if [ "$TEST_SUITE" != anime ]; then
  timeout 30s adb get-state | grep -qx device || fail "Emulator disconnected after instrumentation"
fi
# Anime suite can pass despite a post-run device shutdown ONLY if every screenshot
# was already fully copied to the host and passes pixel integrity checks.
# The watcher is stopped by the EXIT trap, AFTER all screenshots are verified.
# Terminating it here lost the last frame on Android 16 before the final pull.
if [ "$TEST_SUITE" != editor ] && [ "$TEST_SUITE" != humanoid ] && [ "$TEST_SUITE" != anime ]; then
  if [ "$TEST_SUITE" = tree ]; then stages=tree; else stages="$TEST_SUITE-before $TEST_SUITE-after $TEST_SUITE-saved"; fi
  for stage in $stages; do
    pull_proof "mechanical-${stage}.png"
    cp "$PROOF_DIR/mechanical-${stage}.png" "artist-scene-studio-${API_TAG}-mechanical-${stage}.png" || fail "Mechanical $stage screenshot was missing"
    python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-mechanical-${stage}.png" || fail "Mechanical $stage viewport was black"
  done
  echo "Fresh API $API_LEVEL mechanical actors passed with all screenshot proof"
  exit 0
fi
if [ "$TEST_SUITE" = anime ]; then
  for stage in posed neutral female-neutral female-hair-still female-hair-gravity; do
    # Allow the parallel capture helper to finish its atomic copy first.
    for attempt in 1 2 3 4; do
      [ -s "$PROOF_DIR/anime-${stage}.png" ] && break
      sleep 2
    done
    pull_proof "anime-${stage}.png"
    cp "$PROOF_DIR/anime-${stage}.png" "artist-scene-studio-${API_TAG}-anime-${stage}.png" || fail "Anime $stage screenshot was missing"
    python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-anime-${stage}.png" || fail "Anime $stage viewport was black"
  done
  echo "Fresh API $API_LEVEL anime rig pose and neutral deformation proved with screenshots"
  exit 0
fi
if [ "$TEST_SUITE" = editor ]; then
pull_proof "instrumented-viewport.png"
cp "$PROOF_DIR/instrumented-viewport.png" "artist-scene-studio-${API_TAG}-instrumented.png" || fail "Instrumentation screenshot was missing"
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-instrumented.png" || fail "Instrumentation viewport was black"
else
for stage in before hair-short hair-afro hair-bob appearance posed; do
  pull_proof "humanoid-${stage}.png"
  cp "$PROOF_DIR/humanoid-${stage}.png" "artist-scene-studio-${API_TAG}-humanoid-${stage}.png" || fail "Humanoid $stage screenshot was missing"
  python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-humanoid-${stage}.png" || fail "Humanoid $stage viewport was black"
done
fi

echo "Fresh API $API_LEVEL instrumentation passed with all screenshot proof"
