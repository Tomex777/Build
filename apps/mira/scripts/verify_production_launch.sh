#!/usr/bin/env bash
set -euo pipefail
adb install -r dist/mira-production/mira-universal-release.apk
adb shell am start -W -n app.mira.android/.MainActivity | tee dist/mira-production/mira-production-start.txt
sleep 10
test -n "$(adb shell pidof app.mira.android)"
adb shell dumpsys activity activities > dist/mira-production/mira-production-activity.txt
grep -E 'mResumedActivity|topResumedActivity' dist/mira-production/mira-production-activity.txt | grep -q app.mira.android
adb exec-out screencap -p > dist/mira-production/mira-production-launch.png
adb shell uiautomator dump /sdcard/mira-window.xml
adb pull /sdcard/mira-window.xml dist/mira-production/mira-window.xml
grep -q 'text="Library"' dist/mira-production/mira-window.xml
grep -q 'text="Browse"' dist/mira-production/mira-window.xml
grep -q 'text="More"' dist/mira-production/mira-window.xml
if grep -q 'text="Nami"' dist/mira-production/mira-window.xml; then exit 1; fi
