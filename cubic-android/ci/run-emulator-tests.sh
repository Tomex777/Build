#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${CUBIC_APK:-$ROOT/cubic-android/app/build/outputs/apk/debug/app-debug.apk}"
OUT="$ROOT/cubic-android/build/emulator-screenshots"
REPORT="$ROOT/cubic-android/build/ci-report"

mkdir -p "$OUT" "$REPORT"
adb wait-for-device
adb install -r "$APK"
adb shell am force-stop com.tomex777.cubic
adb shell am start -W -n com.tomex777.cubic/.MainActivity | tee "$REPORT/launch.txt"
sleep 4

PID="$(adb shell pidof com.tomex777.cubic | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "Cubic process is not running" >&2
  exit 1
fi
printf '%s\n' "$PID" > "$REPORT/pid.txt"
adb exec-out screencap -p > "$OUT/cubic-home.png"

if ! adb shell dumpsys activity activities | grep -q "com.tomex777.cubic/.MainActivity"; then
  echo "Cubic MainActivity is not resumed" >&2
  exit 1
fi

adb logcat -d -t 300 > "$REPORT/logcat.txt" || true
echo "CUBIC RUNTIME = GREEN" | tee "$REPORT/result.txt"
