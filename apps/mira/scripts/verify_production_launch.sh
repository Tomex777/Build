#!/usr/bin/env bash
set -euo pipefail
evidence="dist/mira-production/api${MIRA_RUNTIME_API:?API must be explicit}"
mkdir -p "$evidence"
adb shell setprop debug.hwui.drawing_enabled 1
test "$(adb shell getprop debug.hwui.drawing_enabled | tr -d '\r')" = "1"
sha256sum dist/mira-production/mira-universal-release.apk > "$evidence/tested-apk-sha256.txt"
trap 'adb logcat -d -v time > "$evidence/logcat.txt"' EXIT
adb install --no-streaming -r dist/mira-production/mira-universal-release.apk
adb shell am start -W -n app.mira.android/.MainActivity | tee "$evidence/start.txt"
sleep 10
test -n "$(adb shell pidof app.mira.android)"
adb shell dumpsys activity activities > "$evidence/activity.txt"
grep -E 'mResumedActivity|topResumedActivity' "$evidence/activity.txt" | grep -q app.mira.android
adb exec-out screencap -p > "$evidence/launch.png"
adb shell uiautomator dump /sdcard/mira-window.xml
adb pull /sdcard/mira-window.xml "$evidence/window.xml"
grep -q 'text="Library"' "$evidence/window.xml"
grep -q 'text="Browse"' "$evidence/window.xml"
grep -q 'text="More"' "$evidence/window.xml"
if grep -q 'text="Nami"' "$evidence/window.xml"; then exit 1; fi
python3 apps/mira/scripts/verify_production_ui.py "$evidence" dist/mira-production/mira-universal-release.apk --real-playback
