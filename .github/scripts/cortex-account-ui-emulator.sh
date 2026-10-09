#!/usr/bin/env bash
set -euo pipefail
adb wait-for-device
adb install -r -g cortex-android/app/build/outputs/apk/debug/app-debug.apk
adb install -r -g cortex-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
# The Android emulator runner invokes an inline script through /bin/sh on Linux.
# Use an explicit bash script and capture both stdout and stderr before checking success.
adb shell am instrument -w -r -e class com.night.cortex.CortexPairingScreenTest com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee cortex-account-ui-results.txt
grep -Eq 'OK \([0-9]+ tests?\)' cortex-account-ui-results.txt
