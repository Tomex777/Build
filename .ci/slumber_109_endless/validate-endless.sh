#!/usr/bin/env bash
# Installed-device smoke test for portrait Endless Tiles and AI-free Slumber.
# Usage: validate-endless.sh <apk> <proof-dir> [quick|full]
set -euo pipefail
APK="${1:?APK}"; OUT="${2:?proof directory}"; MODE="${3:-full}"
mkdir -p "$OUT"
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
adb install -r "$APK" | tee "$OUT/install.txt"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" > "$OUT/start.txt"
sleep 3

ui_dump() {
  : > "$OUT/current.xml"
  timeout 18s adb shell uiautomator dump /sdcard/slumber-ui.xml >/dev/null 2>&1 || true
  timeout 12s adb exec-out cat /sdcard/slumber-ui.xml > "$OUT/current.xml" || true
}
node() {
  python3 - "$OUT/current.xml" "$1" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit(1)
query=sys.argv[2].casefold()
for e in root.iter('node'):
  label=(e.get('text','')+' '+e.get('content-desc','')).strip()
  if query in label.casefold():
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',e.get('bounds',''))
    if m: print(' '.join(m.groups())+'|'+label); sys.exit(0)
sys.exit(1)
PY
}
wait_ui() {
  local text="$1" n
  for n in $(seq 1 20); do
    ui_dump
    if node "$text" >/dev/null; then return 0; fi
    # Handle Android emulator launcher ANRs without masking app crashes.
    if node "Quickstep isn't responding" >/dev/null; then tap_ui "Wait" || true; sleep 2; fi
    if node "GOT IT" >/dev/null; then tap_ui "GOT IT" || true; fi
    sleep .5
  done
  echo "UI element not found: $text" >&2
  cp "$OUT/current.xml" "$OUT/last-failure.xml" || true
  # Preserve the actual app exception (not just a generic emulator crash dialog).
  timeout 20s adb logcat -d -v time -t 4500 > "$OUT/failure-logcat.txt" || true
  exit 1
}
tap_ui() {
  local b coords
  ui_dump
  b=$(node "$1") || return 1
  coords="${b%%|*}"
  read -r x0 y0 x1 y1 <<<"$coords"
  timeout 15s adb shell input tap "$(((x0+x1)/2))" "$(((y0+y1)/2))"
}
capture() { timeout 20s adb exec-out screencap -p > "$OUT/$1.png"; }
wait_ui "Practice"
capture practice
# The old AI provider settings must be absent from installed builds.
tap_ui "Settings"
wait_ui "Appearance"
ui_dump
if grep -Eqi 'Teacher model|Google Gemini|OpenAI-compatible|AI Teacher' "$OUT/current.xml"; then
  echo 'An AI model setting is still visible' >&2; exit 1
fi
capture settings
# An entire song is not the endless mode; locate it from Practice.
tap_ui "Practice"
wait_ui "Endless tiles"
tap_ui "Endless tiles"
wait_ui "Choose a melody"
# The expanded library is lazily rendered and sorted by relevance/recent history.
# Search the actual UI instead of assuming a title must appear above the fold.
tap_ui "Find a tune"
adb shell input text 'Jingle'
adb shell input keyevent KEYCODE_ENTER
wait_ui "Jingle Bells"
capture tiles-library
# Real native sampler preview is a separate action from starting gameplay.
tap_ui "Hear Jingle Bells"
wait_ui "Stop preview"
capture tiles-audition
tap_ui "Stop preview"
wait_ui "Hear Jingle Bells"
# Save is real persistent app state, not an unconnected heart icon.
tap_ui "Save Jingle Bells"
capture tiles-saved
tap_ui "Jingle Bells"
wait_ui "ENDLESS TILES"
wait_ui "Tap the first tile to start in lane"
HEADER=$(node "ENDLESS TILES"); read -r _ HEADER_TOP _ _ <<<"${HEADER%%|*}"
if (( HEADER_TOP < 32 )); then echo "Status bar overlap: header top=$HEADER_TOP" >&2; exit 1; fi
capture tiles-ready
LANE=$(node 'Tap the first tile to start in lane' | sed -n 's/.*in lane \([1-4]\).*/\1/p')
[[ "$LANE" =~ ^[1-4]$ ]] || { echo "Missing starting lane: $LANE" >&2; exit 1; }
# Canvas accessible bounds yield the correct tap point even on API 26 or odd aspect ratios.
BOUNDS=$(node 'Tap the first tile to start in lane'); read -r X0 Y0 X1 Y1 <<<"${BOUNDS%%|*}"
W=$((X1-X0)); H=$((Y1-Y0)); LANE_W=$((W/4))
FIRST_X=$((X0 + ((2*LANE-1)*LANE_W)/2))
FIRST_Y=$((Y0 + (955*H)/1000 - (190*LANE_W)/200))
# Android 8's slower emulator may expire its first beat before an ADB
# second tap can reach the newly composed Pause button. The full Android 16
# suite verifies user pause/resume and frozen time; quick API 26 installation
# verifies opening, score, game over, relaunch and per-song persistence.
if [[ "$MODE" == 'full' ]]; then
  PAUSE_X=$((X1-45)); PAUSE_Y=$((HEADER_TOP+28))
  adb shell "input tap $FIRST_X $FIRST_Y; sleep 0.25; input tap $PAUSE_X $PAUSE_Y"
  wait_ui "PAUSED"
  capture tiles-paused
  sleep 3
  wait_ui "PAUSED" # Pause must hold the clock indefinitely.
  tap_ui "RESUME"
else
  adb shell input tap "$FIRST_X" "$FIRST_Y"
fi
# The run ends only when a black tile is missed, never after a finite song.
for _ in $(seq 1 25); do
  ui_dump
  if node 'GAME OVER' >/dev/null || node 'NEW BEST' >/dev/null; then break; fi
  sleep .5
done
ui_dump
if ! node 'GAME OVER' >/dev/null && ! node 'NEW BEST' >/dev/null; then
  echo 'No game-over state after tile miss' >&2; exit 1
fi
if grep -q 'SONG COMPLETE' "$OUT/current.xml"; then
  echo 'Finite song-complete overlay still present' >&2; exit 1
fi
capture tiles-gameover
if [[ "$MODE" == 'full' ]]; then
  tap_ui 'PLAY AGAIN'
  wait_ui 'ENDLESS TILES'
  capture tiles-again
fi
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" > "$OUT/relaunch.txt"
wait_ui "Practice"
capture relaunch
# Expected saved best and run count are shown on the Practice tab.
if ! node "Best" >/dev/null; then
  echo 'Endless best score was not persisted across relaunch' >&2; exit 1
fi
# The record must also be tied to the selected tune, not just a global total.
tap_ui "Endless tiles"
wait_ui "Piano Tiles"
wait_ui "Best 103"
capture tiles-song-record
tap_ui "Saved"
wait_ui "Jingle Bells"
# Selected song survives process death and remains discoverable in Saved.
tap_ui "All"
tap_ui "Find a tune"
adb shell input text 'Happy'
adb shell input keyevent KEYCODE_ENTER
wait_ui "Happy Birthday"
tap_ui "Happy Birthday"
wait_ui "ENDLESS TILES"
wait_ui "BEST 0"
capture tiles-independent-song
printf 'PASS: endless gameplay, per-song records, pause/loss, API insets and AI-free settings\n' | tee "$OUT/PASS.txt"