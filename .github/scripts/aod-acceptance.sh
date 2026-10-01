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
adb shell am instrument -w -r -e notClass com.homira.aod.ReleaseSmokeTest com.homira.aod.test/androidx.test.runner.AndroidJUnitRunner | tee aod-evidence/instrumentation.txt
if ! grep -Eq 'OK \([0-9]+ tests\)' aod-evidence/instrumentation.txt; then exit 1; fi
# A real process restart, separate from ActivityScenario.recreate.
adb shell run-as com.homira.aod cat files/designs.json > aod-evidence/before-restart.json
adb shell am force-stop com.homira.aod
adb shell am start -W -n com.homira.aod/.MainActivity | tee aod-evidence/restart.txt
adb shell uiautomator dump /sdcard/aod-ui.xml
adb pull /sdcard/aod-ui.xml aod-evidence/restart-ui.xml
grep -Eq 'AOD' aod-evidence/restart-ui.xml
adb shell run-as com.homira.aod cat files/designs.json > aod-evidence/after-restart.json
cmp aod-evidence/before-restart.json aod-evidence/after-restart.json
python3 - <<'PY2'
import json
with open('aod-evidence/after-restart.json') as f: themes=json.load(f)['themes']
assert any(t['name']=='Acceptance design' and len(t['elements'])==2 for t in themes)
PY2
adb exec-out screencap -p > aod-evidence/restart.png
# Register the actual system dream and record platform response.
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_components com.homira.aod/.AmbientService
adb root
adb wait-for-device
adb shell cmd dreams start-dreaming > aod-evidence/dream-start.txt 2>&1 || true
for attempt in $(seq 1 20); do
  adb shell dumpsys dreams > aod-evidence/dream-running.txt
  if grep -Eq 'mCurrentDream.*com.homira.aod' aod-evidence/dream-running.txt; then break; fi
  sleep 0.5
done
adb shell dumpsys dreams > aod-evidence/dream-running.txt
adb exec-out screencap -p > aod-evidence/ambient-system.png
adb shell cmd dreams stop-dreaming > aod-evidence/dream-stop.txt 2>&1 || true
adb shell input keyevent 82

# Exercise the same minified release code with a clearly labelled CI QA signing key.
release=$(find build-artifacts -name 'AOD-minified-QA.apk' -print -quit)
test -s "$release"
adb install -r "$release"
python3 .github/scripts/aod-release-acceptance.py | tee aod-evidence/release-acceptance.txt
# Reinstall/upgrade must retain designs and remain launchable.
adb install -r "$release"
python3 .github/scripts/aod-release-acceptance.py | tee aod-evidence/upgrade-acceptance.txt
