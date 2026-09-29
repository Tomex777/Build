#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${CUBIC_APK:-$ROOT/cubic-android/app/build/outputs/apk/debug/app-debug.apk}"
OUT="$ROOT/cubic-android/build/emulator-screenshots"
REPORT="$ROOT/cubic-android/build/ci-report"

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
  sleep 0.25
}

adb wait-for-device
adb install -r "$APK"
adb shell am force-stop com.tomex777.cubic
adb shell am start -W -n com.tomex777.cubic/.MainActivity | tee "$REPORT/launch.txt"
sleep 3

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

adb shell dumpsys package com.tomex777.cubic > "$REPORT/package.txt"
grep -q "versionName=1.0.0" "$REPORT/package.txt"

assert_text "Cubic"
assert_text "Scramble"
assert_text "Learn"
adb exec-out screencap -p > "$OUT/cubic-home.png"

tap_text "Scramble"
assert_text "Scrambled"
adb exec-out screencap -p > "$OUT/cubic-scrambled.png"

tap_text "Learn"
assert_text "Next move:"

for _ in $(seq 1 30); do
  dump_ui
  if ! grep -Fq "Next move:" "$REPORT/window.xml"; then
    break
  fi
  tap_text "Do this move"
done

assert_text "Solved"
adb exec-out screencap -p > "$OUT/cubic-guided-solved.png"

tap_text "Play"
assert_text "Scramble"

adb logcat -d -t 500 > "$REPORT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|AndroidRuntime: FATAL" "$REPORT/logcat.txt"; then
  echo "Fatal exception found in Cubic logcat" >&2
  exit 1
fi

echo "CUBIC RELEASE RUNTIME = GREEN" | tee "$REPORT/result.txt"
