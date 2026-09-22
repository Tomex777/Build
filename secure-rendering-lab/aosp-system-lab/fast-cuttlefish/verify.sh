#!/usr/bin/env bash
set -euo pipefail

PKG="com.tomex.securerenderlab.system"

adb wait-for-device

echo "=== Device ==="
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.type
adb shell getprop ro.debuggable

echo
echo "=== Package flags/signature privileges ==="
adb shell dumpsys package "$PKG" | grep -E   "userId=|pkgFlags=|privateFlags=|CAPTURE_SECURE_VIDEO_OUTPUT|CAPTURE_VIDEO_OUTPUT|CAPTURE_BLACKOUT_CONTENT|READ_FRAME_BUFFER" || true

echo
echo "=== Package location ==="
adb shell pm path "$PKG" || true

echo
echo "=== Launch ==="
adb shell am start -n "$PKG/.SystemLabActivity"
