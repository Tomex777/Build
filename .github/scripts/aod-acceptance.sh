#!/usr/bin/env bash
set -euo pipefail
mkdir -p aod-evidence
trap 'adb exec-out screencap -p > aod-evidence/final-surface.png; adb shell uiautomator dump /sdcard/aod-final.xml; adb pull /sdcard/aod-final.xml aod-evidence/final-ui.xml; adb logcat -d > aod-evidence/logcat.txt; adb shell dumpsys activity activities > aod-evidence/activities.txt; adb shell dumpsys dreams > aod-evidence/dreams.txt; adb pull /sdcard/Android/data/com.homira.aod/files/screenshots aod-evidence/ || true' EXIT
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
adb shell settings put secure screensaver_activate_on_sleep 1
adb root
adb wait-for-device
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$api" = 26 ]; then
  # API 26 predates Dream shell commands. These root-only system test transactions
  # are IDreamManager.dream/awaken; the application never uses hidden APIs.
  adb shell service call dreams 1 > aod-evidence/dream-start.txt
else
  adb shell cmd dreams start-dreaming > aod-evidence/dream-start.txt 2>&1
fi
for attempt in $(seq 1 20); do
  adb shell dumpsys dreams > aod-evidence/dream-running.txt
  if grep -Eq 'mCurrentDream.*com.homira.aod' aod-evidence/dream-running.txt; then break; fi
  sleep 0.5
done
adb shell dumpsys dreams > aod-evidence/dream-running.txt
grep -Eq 'mCurrentDream.*com.homira.aod' aod-evidence/dream-running.txt
sleep 0.5
adb exec-out screencap -p > aod-evidence/ambient-system.png
python3 - <<'PY3'
from PIL import Image
import subprocess, time
for attempt in range(30):
    with open('aod-evidence/ambient-system.png', 'wb') as out:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=out, check=True)
    image = Image.open('aod-evidence/ambient-system.png').convert('RGB')
    w, h = image.size
    pixels = [image.getpixel((x,y)) for y in range(h//5, h*4//5, 4)
              for x in range(w//5, w*4//5, 4)]
    visible = sum(min(p) > 100 for p in pixels)
    black = sum(max(p) < 5 for p in pixels) / len(pixels)
    if visible > 20 and black > .9:
        break
    time.sleep(.5)
else:
    raise AssertionError('System Dream must render visible content on its pure-black surface')
print('System Dream rendering verified:', visible, 'visible content samples')
PY3
adb shell dumpsys window windows > aod-evidence/ambient-window.txt
if [ "$api" = 26 ]; then
  adb shell service call dreams 2 > aod-evidence/dream-stop.txt
else
  adb shell cmd dreams stop-dreaming > aod-evidence/dream-stop.txt 2>&1
fi
adb shell input keyevent 82

# Exercise the same minified release code with a clearly labelled CI QA signing key.
release=$(find build-artifacts -name 'AOD-minified-QA.apk' -print -quit)
test -s "$release"
adb install -r "$release"
python3 .github/scripts/aod-release-acceptance.py | tee aod-evidence/release-acceptance.txt
# Reinstall/upgrade must retain designs and remain launchable.
adb install -r "$release"
python3 .github/scripts/aod-release-acceptance.py | tee aod-evidence/upgrade-acceptance.txt

# Once permanent secrets are configured, test that exact production identity too.
production=$(find build-artifacts -name 'AOD-production-universal.apk' -print -quit)
if [ -n "$production" ]; then
  adb uninstall com.homira.aod
  adb install "$production"
  python3 .github/scripts/aod-release-acceptance.py | tee aod-evidence/production-install.txt
  adb install -r "$production"
  python3 .github/scripts/aod-release-acceptance.py | tee aod-evidence/production-upgrade.txt
else
  echo 'Production identity not configured; QA acceptance is not production signing.' > aod-evidence/production-pending.txt
fi
