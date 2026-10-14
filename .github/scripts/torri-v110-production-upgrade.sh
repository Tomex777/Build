#!/usr/bin/env bash
set -euo pipefail
ACTIVITY="app.torri/eu.kanade.tachiyomi.ui.main.MainActivity"
adb install "$RUNNER_TEMP/torri-previous-v1.0.0.apk"
adb shell dumpsys package app.torri | grep 'versionCode=10000'
adb logcat -c
adb shell am start -W -n "$ACTIVITY"
sleep 18
oldPID="$(adb shell pidof app.torri 2>/dev/null | tr -d '\r' || true)"
test -n "$oldPID" || { echo "::error::Torri 1.0.0 failed to start"; adb logcat -d -b crash | tail -100; exit 1; }
# DO NOT uninstall: preserve app.torri's existing data and signing continuity.
adb shell input keyevent KEYCODE_HOME
adb install -r "dist/Torri-1.1.0-universal-release.apk"
adb shell dumpsys package app.torri | grep 'versionCode=10001'
adb logcat -c
adb shell am start -W -n "$ACTIVITY"
sleep 28
newPID="$(adb shell pidof app.torri 2>/dev/null | tr -d '\r' || true)"
test -n "$newPID" || { echo "::error::Torri 1.1.0 failed after in-place upgrade"; adb logcat -d -b crash | tail -150; exit 1; }
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n "$ACTIVITY"
sleep 6
adb shell pidof app.torri
if adb logcat -d -b crash | grep -q 'Process: app.torri'; then
 echo "::error::Torri crashed during in-place upgrade/resume"
 adb logcat -d -b crash | tail -150
 exit 1
fi
adb exec-out screencap -p > "$RUNNER_TEMP/torri-v110-upgrade-android15.png"
test -s "$RUNNER_TEMP/torri-v110-upgrade-android15.png"
echo "PASS: REAL permanent-signed Torri 1.0.0 upgraded IN PLACE to Torri 1.1.0, with no app uninstall."
