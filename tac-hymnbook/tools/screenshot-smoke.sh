#!/usr/bin/env bash
set -euo pipefail

APK="./dist/app-debug.apk"
test -f "$APK"
adb wait-for-device
adb shell getprop sys.boot_completed
adb shell input keyevent 82 || true

echo "Installing APK ..."
if ! timeout 90s adb install --no-streaming -r "$APK"; then
  echo "Direct install did not complete; attempting staged install"
  timeout 60s adb push "$APK" /data/local/tmp/tac-hymns.apk
  timeout 90s adb shell pm install -r /data/local/tmp/tac-hymns.apk
fi

echo "Opening home"
timeout 25s adb shell am start -W -n com.night.tachymns/.MainActivity
sleep 5
timeout 15s adb shell pidof com.night.tachymns
timeout 35s adb exec-out screencap -p > tac-hymns-home.png
file tac-hymns-home.png

echo "Switching the actual app to dark mode"
# The Compose theme toggle is in the home toolbar on this 1080x1920 Pixel 2 emulator.
timeout 15s adb shell input tap 908 250
sleep 3
timeout 35s adb exec-out screencap -p > tac-hymns-dark-home.png
file tac-hymns-dark-home.png

echo "Opening Yoruba hymn reader in persisted dark mode"
timeout 20s adb shell am force-stop com.night.tachymns
timeout 25s adb shell am start -W -n com.night.tachymns/.MainActivity --ei preview_hymn 4
sleep 4
timeout 15s adb shell pidof com.night.tachymns
timeout 35s adb exec-out screencap -p > tac-hymns-reader.png
file tac-hymns-reader.png
cp tac-hymns-reader.png tac-hymns-dark-reader.png

echo "Checking for app crashes"
if adb logcat -d -s AndroidRuntime:E | grep -A 8 'Process: com.night.tachymns'; then
  exit 1
fi
