#!/usr/bin/env bash
set -euo pipefail
API="${1:?API level required}"
OUT="poetry-api${API}"
trap 'adb logcat -d -t 8000 > "'"${OUT}"'-logcat.txt" 2>/dev/null || true' EXIT

APP="$(find /tmp/poetry-build -type f -name 'app-debug.apk' -print -quit)"
TEST="$(find /tmp/poetry-build -type f -name '*androidTest*.apk' -print -quit)"
test -s "$APP" || { echo "Missing debug APK"; exit 2; }
test -s "$TEST" || { echo "Missing androidTest APK"; exit 2; }
adb install -r "$APP"
adb install -r "$TEST"

# Keep an actual device screenshot even if the instrumentation test fails.
adb shell am start -n com.night.poetry/.MainActivity
sleep 4
adb shell screencap -p /sdcard/poetry-home.png
adb pull /sdcard/poetry-home.png "${OUT}-home.png"

adb shell am instrument -w -r com.night.poetry.test/androidx.test.runner.AndroidJUnitRunner | tee "${OUT}-instrumentation.txt"
grep -Fq 'OK (1 test)' "${OUT}-instrumentation.txt" || {
  echo "Instrumentation did not report success" >&2
  cat "${OUT}-instrumentation.txt" >&2
  exit 1
}
echo "PASS: Poetry API ${API} app launch, screenshot and Compose smoke test"
