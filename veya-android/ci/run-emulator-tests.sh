#!/usr/bin/env bash
set -euo pipefail

APK="${VEYA_APK:?VEYA_APK must point to the built APK}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHOT_DIR="$ROOT/build/emulator-screenshots"
REPORT_DIR="$ROOT/build/ci-report"
mkdir -p "$SHOT_DIR" "$REPORT_DIR"

adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb install -r -g "$APK"

adb shell am force-stop com.veya.app || true
adb shell am start -W -n com.veya.app/.MainActivity | tee "$REPORT_DIR/am-start.txt"

for _ in $(seq 1 20); do
  if adb shell pidof com.veya.app >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

PID="$(adb shell pidof com.veya.app | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  echo "Veya process is not running" >&2
  adb logcat -d > "$REPORT_DIR/logcat.txt" || true
  exit 1
fi

echo "$PID" > "$REPORT_DIR/pid.txt"
adb shell dumpsys activity activities > "$REPORT_DIR/activities.txt"
adb shell dumpsys package com.veya.app > "$REPORT_DIR/package.txt"
adb logcat -d > "$REPORT_DIR/logcat.txt" || true

if ! grep -q 'com.veya.app/.MainActivity' "$REPORT_DIR/activities.txt"; then
  echo "Veya MainActivity is not present in activity state" >&2
  exit 1
fi

capture() {
  local name="$1"
  sleep 1
  adb exec-out screencap -p > "$SHOT_DIR/$name.png"
  test -s "$SHOT_DIR/$name.png"
  local bytes
  bytes="$(wc -c < "$SHOT_DIR/$name.png")"
  if [[ "$bytes" -lt 10000 ]]; then
    echo "Screenshot $name is suspiciously small: $bytes bytes" >&2
    exit 1
  fi
}

capture home

SIZE="$(adb shell wm size | tr -d '\r' | tail -n1 | grep -oE '[0-9]+x[0-9]+' || true)"
WIDTH="${SIZE%x*}"
HEIGHT="${SIZE#*x}"
if [[ "$WIDTH" =~ ^[0-9]+$ && "$HEIGHT" =~ ^[0-9]+$ ]]; then
  Y=$((HEIGHT - 70))
  adb shell input tap $((WIDTH / 2)) "$Y"
  capture downloads
  adb shell input tap $((WIDTH * 5 / 6)) "$Y"
  capture settings
fi

adb shell uiautomator dump /sdcard/veya-window.xml >/dev/null 2>&1 || true
adb pull /sdcard/veya-window.xml "$REPORT_DIR/window.xml" >/dev/null 2>&1 || true

if [[ -f "$REPORT_DIR/window.xml" ]] && ! grep -q 'Veya\|Settings\|Downloads' "$REPORT_DIR/window.xml"; then
  echo "Warning: accessibility dump did not expose expected Veya text" | tee "$REPORT_DIR/ui-warning.txt"
fi

if grep -E 'FATAL EXCEPTION|Process: com\.veya\.app.*FATAL' "$REPORT_DIR/logcat.txt"; then
  echo "Fatal exception detected in Veya logcat" >&2
  exit 1
fi

echo "runtime=PASS" | tee "$REPORT_DIR/status.txt"
echo "package=com.veya.app" >> "$REPORT_DIR/status.txt"
echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')" >> "$REPORT_DIR/status.txt"
