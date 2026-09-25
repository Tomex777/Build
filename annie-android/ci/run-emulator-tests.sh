#!/usr/bin/env bash
set -euo pipefail

set +e
gradle --no-daemon --stacktrace -p annie-android :app:connectedDebugAndroidTest
TEST_STATUS=$?
set -e

echo "===== Android instrumented test XML ====="
find annie-android/app/build/outputs/androidTest-results -type f -name '*.xml' -print -exec cat {} \; 2>/dev/null || true

SCREENSHOT_DIR="annie-android/build/emulator-screenshots"
mkdir -p "$SCREENSHOT_DIR"

echo "===== Screenshot MediaStore diagnostics ====="
adb shell ls -la /sdcard/Pictures/AnnieCI || true
adb pull /sdcard/Pictures/AnnieCI "$SCREENSHOT_DIR" || true

if [ "$TEST_STATUS" -ne 0 ]; then
    adb logcat -d | grep -Ei 'libvlc|vlc|vout|video output|get_buffer|decoder|h264|surface|texture|android_display' > "$SCREENSHOT_DIR/vlc-logcat.txt" || true
fi

if [ "$TEST_STATUS" -eq 0 ] && [ -z "$(find "$SCREENSHOT_DIR" -type f -name '*.png' -print -quit)" ]; then
    echo "::error::Emulator tests passed without producing retrievable screenshots."
    TEST_STATUS=1
fi

adb shell rm -rf /sdcard/Pictures/AnnieCI || true
exit "$TEST_STATUS"
