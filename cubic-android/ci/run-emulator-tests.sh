#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${CUBIC_APK:-$ROOT/cubic-android/app/build/outputs/apk/debug/app-debug.apk}"
EXTENDED="${CUBIC_EXTENDED:-0}"
OUT="$ROOT/cubic-android/build/emulator-screenshots"
REPORT="$ROOT/cubic-android/build/ci-report"

rm -rf "$OUT" "$REPORT"
mkdir -p "$OUT" "$REPORT"

refresh_ui() {
  local attempt
  for attempt in 1 2 3; do
    if adb shell uiautomator dump /sdcard/cubic-window.xml >/dev/null 2>&1 &&
       adb pull /sdcard/cubic-window.xml "$REPORT/window.xml" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.4
  done
  echo "Unable to refresh Cubic UI hierarchy" >&2
  return 1
}

wait_for_text() {
  local wanted="$1"
  local attempts="${2:-12}"
  local attempt
  for attempt in $(seq 1 "$attempts"); do
    refresh_ui
    if grep -Fq "$wanted" "$REPORT/window.xml"; then
      return 0
    fi
    sleep 0.35
  done
  echo "Timed out waiting for UI text: $wanted" >&2
  cat "$REPORT/window.xml" >&2
  exit 1
}

assert_cached() {
  local wanted="$1"
  if ! grep -Fq "$wanted" "$REPORT/window.xml"; then
    echo "Expected UI text not found: $wanted" >&2
    cat "$REPORT/window.xml" >&2
    exit 1
  fi
}

assert_text() {
  refresh_ui
  assert_cached "$1"
}

coords_from_cache() {
  local wanted="$1"
  python3 - "$REPORT/window.xml" "$wanted" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()

for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    if text == wanted or desc == wanted or wanted in text or wanted in desc:
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            left, top, right, bottom = map(int, match.groups())
            print((left + right) // 2, (top + bottom) // 2)
            raise SystemExit(0)

raise SystemExit(2)
PY
}

tap_cached() {
  local wanted="$1"
  local coords
  coords="$(coords_from_cache "$wanted")" || {
    echo "Could not locate cached control: $wanted" >&2
    cat "$REPORT/window.xml" >&2
    exit 1
  }
  read -r x y <<<"$coords"
  adb shell input tap "$x" "$y"
  sleep 0.2
}

tap_repeat_cached() {
  local wanted="$1"
  local count="$2"
  for _ in $(seq 1 "$count"); do
    tap_cached "$wanted"
  done
}

capture_puzzle_and_reopen() {
  local file_name="$1"
  local expected="$2"

  refresh_ui
  tap_cached "Close controls"
  sleep 0.35
  wait_for_text "3D puzzle ready"
  assert_cached "$expected"
  adb exec-out screencap -p > "$OUT/$file_name"

  refresh_ui
  tap_cached "Open controls"
  sleep 0.35
  refresh_ui
}

launch_app() {
  adb shell am force-stop com.tomex777.cubic
  adb shell am start -W -n com.tomex777.cubic/.MainActivity | tee "$REPORT/launch.txt"
  sleep 2
}

adb wait-for-device
adb install -r "$APK"
launch_app

PID="$(adb shell pidof com.tomex777.cubic | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "Cubic process is not running" >&2
  exit 1
fi
printf '%s\n' "$PID" > "$REPORT/pid.txt"

if ! adb shell dumpsys activity activities | grep -q "com.tomex777.cubic/.MainActivity"; then
  echo "Cubic MainActivity is not resumed" >&2
  exit 1
fi

adb shell getprop ro.build.version.sdk | tr -d '\r' > "$REPORT/device-api.txt"
adb shell dumpsys package com.tomex777.cubic > "$REPORT/package.txt"
grep -q "versionName=1.0.0" "$REPORT/package.txt"

wait_for_text "3D puzzle ready"
assert_cached "Cubic"
assert_cached "3 × 3 × 3"
assert_cached "Solved"
assert_cached "Controls"
adb exec-out screencap -p > "$OUT/cubic-home.png"

# Prove that the actual GLSurfaceView receives orbit gestures.
adb shell input touchscreen swipe 260 650 800 560 450
sleep 0.7
adb exec-out screencap -p > "$OUT/cubic-orbit.png"
if [[ "$(sha256sum "$OUT/cubic-home.png" | cut -d' ' -f1)" == "$(sha256sum "$OUT/cubic-orbit.png" | cut -d' ' -f1)" ]]; then
  echo "Orbit gesture did not change the rendered frame" >&2
  exit 1
fi

refresh_ui
tap_cached "Open controls"
sleep 0.4
refresh_ui
assert_cached "Counterclockwise"
adb exec-out screencap -p > "$OUT/cubic-controls-sheet.png"

if [[ "$EXTENDED" == "1" ]]; then
  # Reuse the same cached bottom-sheet control bounds to avoid repeatedly
  # starting uiautomator on the emulator.
  for face in R L U D F B; do
    tap_cached "Face $face"
    tap_cached "Turn clockwise"
    tap_cached "Turn counterclockwise"
  done
  refresh_ui
  assert_cached "Solved"

  # 2x2x2.
  tap_cached "Decrease Width"
  tap_cached "Decrease Height"
  tap_cached "Decrease Depth"
  refresh_ui
  assert_cached "2 × 2 × 2"
  assert_cached "Solved"
  capture_puzzle_and_reopen "cubic-2x2x2.png" "2 × 2 × 2"

  # 4x4x4.
  tap_cached "Increase Width"
  tap_cached "Increase Height"
  tap_cached "Increase Depth"
  tap_cached "Increase Width"
  tap_cached "Increase Height"
  tap_cached "Increase Depth"
  refresh_ui
  assert_cached "4 × 4 × 4"

  # Inner R layer 2 in both directions.
  tap_cached "Face R"
  tap_cached "Next layer"
  tap_cached "Turn clockwise"
  tap_cached "Turn counterclockwise"
  refresh_ui
  assert_cached "Layer 2 of 4"
  assert_cached "Solved"

  # 3x3x5 cuboid. R is a rectangular 3x5 section, so two requested
  # clockwise turns are two legal half-turns and must return to solved.
  tap_cached "Decrease Width"
  tap_cached "Decrease Height"
  tap_cached "Increase Depth"
  refresh_ui
  assert_cached "3 × 3 × 5"
  capture_puzzle_and_reopen "cubic-3x3x5.png" "3 × 3 × 5"

  tap_cached "Face R"
  tap_cached "Turn clockwise"
  tap_cached "Turn clockwise"
  refresh_ui
  assert_cached "Solved"

  # 2x4x6 true cuboid: scramble, undo, reset.
  tap_cached "Decrease Width"
  tap_cached "Increase Height"
  tap_cached "Increase Depth"
  refresh_ui
  assert_cached "2 × 4 × 6"

  tap_cached "Scramble"
  refresh_ui
  assert_cached "18 moves"
  capture_puzzle_and_reopen "cubic-2x4x6-scrambled.png" "18 moves"

  tap_cached "Undo"
  refresh_ui
  assert_cached "17 moves"

  tap_cached "Reset"
  refresh_ui
  assert_cached "Solved"

  # Larger practical runtime sample.
  tap_repeat_cached "Increase Width" 4
  tap_repeat_cached "Increase Height" 2
  refresh_ui
  assert_cached "6 × 6 × 6"
  assert_cached "Solved"
  capture_puzzle_and_reopen "cubic-6x6x6.png" "6 × 6 × 6"
fi

# Cold-start for the guided 3x3 proof.
launch_app
wait_for_text "3D puzzle ready"
assert_cached "3 × 3 × 3"
assert_cached "Solved"
assert_cached "Controls"

tap_cached "Open controls"
sleep 0.4
refresh_ui
tap_cached "Scramble"
refresh_ui
assert_cached "18 moves"
capture_puzzle_and_reopen "cubic-scrambled.png" "18 moves"

tap_cached "Learn"
sleep 0.4
refresh_ui
assert_cached "Next move:"
adb exec-out screencap -p > "$OUT/cubic-guided-step.png"

# The scramble contains exactly 18 recorded legal moves. The Learn button
# stays at a stable bottom-row position while each instruction updates.
tap_repeat_cached "Do this move" 18

refresh_ui
assert_cached "3 × 3 × 3"
assert_cached "Solved"
capture_puzzle_and_reopen "cubic-guided-solved.png" "Solved"

tap_cached "Play"
tap_cached "Close controls"
sleep 0.3
wait_for_text "3D puzzle ready"
assert_cached "Controls"

adb logcat -d -t 700 > "$REPORT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|AndroidRuntime: FATAL" "$REPORT/logcat.txt"; then
  echo "Fatal exception found in Cubic logcat" >&2
  exit 1
fi

echo "CUBIC RELEASE RUNTIME = GREEN" | tee "$REPORT/result.txt"
