#!/usr/bin/env bash
set -euo pipefail
mkdir -p aod-evidence
trap 'adb logcat -d > aod-evidence/logcat.txt; adb shell dumpsys activity activities > aod-evidence/activities.txt; adb shell dumpsys dreams > aod-evidence/dreams.txt; adb pull /sdcard/Android/data/com.homira.aod/files/screenshots aod-evidence/ || true' EXIT
apk=$(find build-artifacts -name 'app-debug.apk' -print -quit)
test_apk=$(find build-artifacts -name 'app-debug-androidTest.apk' -print -quit)
test -s "$apk"
test -s "$test_apk"
adb wait-for-device
adb shell input keyevent 82
adb install -r "$apk"
adb install -r "$test_apk"
adb shell am instrument -w -r com.homira.aod.test/androidx.test.runner.AndroidJUnitRunner | tee aod-evidence/instrumentation.txt
if ! rg -q 'OK \([0-9]+ tests\)' aod-evidence/instrumentation.txt; then exit 1; fi
# A real process restart, separate from ActivityScenario.recreate.
adb shell am force-stop com.homira.aod
adb shell am start -W -n com.homira.aod/.MainActivity | tee aod-evidence/restart.txt
adb shell uiautomator dump /sdcard/aod-ui.xml
adb pull /sdcard/aod-ui.xml aod-evidence/restart-ui.xml
rg -q 'Acceptance design' aod-evidence/restart-ui.xml
adb exec-out screencap -p > aod-evidence/restart.png
# Register the actual system dream and record platform response.
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_components com.homira.aod/.AmbientService
adb shell cmd dreams start-dreaming > aod-evidence/dream-start.txt 2>&1 || true
