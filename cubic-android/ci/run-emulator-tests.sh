#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${CUBIC_APK:-$ROOT/cubic-android/app/build/outputs/apk/debug/app-debug.apk}"
EXTENDED="${CUBIC_EXTENDED:-0}"
OUT="$ROOT/cubic-android/build/emulator-screenshots"
REPORT="$ROOT/cubic-android/build/ci-report"

rm -rf "$OUT" "$REPORT"
mkdir -p "$OUT" "$REPORT"

dump_ui() {
  adb shell uiautomator dump /sdcard/cubic-window.xml >/dev/null
  adb pull /sdcard/cubic-window.xml "$REPORT/window.xml" >/dev/null
}

assert_text() {
  local wanted="$1"
  dump_ui
  if ! grep -Fq "$wanted" "$REPORT/window.xml"; then
    echo "Expected UI text not found: $wanted" >&2
    cat "$REPORT/window.xml" >&2
    exit 1
  fi
}

tap_text() {
  local wanted="$1"
  dump_ui
  local coords
  coords="$(python3 - "$REPORT/window.xml" "$wanted" <<'PY'
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
)" || {
    echo "Could not locate tappable text: $wanted" >&2
    cat "$REPORT/window.xml" >&2
    exit 1
  }
  read -r x y <<<"$coords"
  adb shell input tap "$x" "$y"
  sleep 0.3
}

tap_repeat() {
  local wanted="$1"
  local count="$2"
  for _ in $(seq 1 "$count"); do
    tap_text "$wanted"
  done
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

assert_text "Cubic"
assert_text "3 × 3 × 3"
assert_text "Solved"
assert_text "Counterclockwise"
adb exec-out screencap -p > "$OUT/cubic-home.png"

# Exercise the actual 3D viewport orbit gesture and confirm the process survives.
adb shell input swipe 300 700 680 620 300
sleep 0.5
adb exec-out screencap -p > "$OUT/cubic-orbit.png"

if [[ "$EXTENDED" == "1" ]]; then
  # Every outer face, both directions, must return a solved 3x3.
  for face in R L U D F B; do
    tap_text "Face $face"
    tap_text "Turn clockwise"
    assert_text "1 move"
    tap_text "Turn counterclockwise"
    assert_text "Solved"
  done

  # 2x2x2.
  tap_text "Decrease Width"
  tap_text "Decrease Height"
  tap_text "Decrease Depth"
  assert_text "2 × 2 × 2"
  adb exec-out screencap -p > "$OUT/cubic-2x2x2.png"

  # 3x3x3 then 4x4x4.
  tap_text "Increase Width"
  tap_text "Increase Height"
  tap_text "Increase Depth"
  assert_text "3 × 3 × 3"

  tap_text "Increase Width"
  tap_text "Increase Height"
  tap_text "Increase Depth"
  assert_text "4 × 4 × 4"

  # Inner layer: R layer 2 clockwise + counterclockwise must be reversible.
  tap_text "Face R"
  tap_text "Next layer"
  assert_text "Layer 2 of 4"
  tap_text "Turn clockwise"
  assert_text "1 move"
  tap_text "Turn counterclockwise"
  assert_text "Solved"

  # 3x3x5 cuboid. R has a rectangular 3x5 cross-section, so each requested
  # quarter-turn becomes a legal half-turn; two turns return to solved.
  tap_text "Decrease Width"
  tap_text "Decrease Height"
  tap_text "Increase Depth"
  assert_text "3 × 3 × 5"
  adb exec-out screencap -p > "$OUT/cubic-3x3x5.png"

  tap_text "Face R"
  tap_text "Turn clockwise"
  assert_text "1 move"
  tap_text "Turn clockwise"
  assert_text "Solved"

  # 2x4x6: prove scramble, undo and reset on a true cuboid.
  tap_text "Decrease Width"
  tap_text "Increase Height"
  tap_text "Increase Depth"
  assert_text "2 × 4 × 6"
  tap_text "Scramble"
  assert_text "18 moves"
  adb exec-out screencap -p > "$OUT/cubic-2x4x6-scrambled.png"
  tap_text "Undo"
  assert_text "17 moves"
  tap_text "Reset"
  assert_text "Solved"

  # Larger practical runtime sample.
  tap_repeat "Increase Width" 4
  tap_repeat "Increase Height" 2
  assert_text "6 × 6 × 6"
  adb exec-out screencap -p > "$OUT/cubic-6x6x6.png"
fi

# Cold-start again so the guided-solve proof starts from the default 3x3.
launch_app
assert_text "3 × 3 × 3"
assert_text "Solved"

tap_text "Scramble"
assert_text "18 moves"
adb exec-out screencap -p > "$OUT/cubic-scrambled.png"

tap_text "Learn"
assert_text "Next move:"
adb exec-out screencap -p > "$OUT/cubic-guided-step.png"

for _ in $(seq 1 30); do
  dump_ui
  if ! grep -Fq "Next move:" "$REPORT/window.xml"; then
    break
  fi
  tap_text "Do this move"
done

assert_text "3 × 3 × 3"
assert_text "Solved"
adb exec-out screencap -p > "$OUT/cubic-guided-solved.png"

tap_text "Play"
assert_text "Scramble"

adb logcat -d -t 700 > "$REPORT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|AndroidRuntime: FATAL" "$REPORT/logcat.txt"; then
  echo "Fatal exception found in Cubic logcat" >&2
  exit 1
fi

echo "CUBIC RELEASE RUNTIME = GREEN" | tee "$REPORT/result.txt"
