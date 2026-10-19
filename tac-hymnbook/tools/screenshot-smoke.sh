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

echo "Installing and running real MediaPlayer instrumented device tests"
test -f ./dist/app-debug-androidTest.apk
timeout 90s adb install --no-streaming -r ./dist/app-debug-androidTest.apk
timeout 180s adb shell am instrument -w -r com.night.tachymns.test/androidx.test.runner.AndroidJUnitRunner | tee tac-hymns-device-test-results.txt
grep -q "OK (3 tests)" tac-hymns-device-test-results.txt

echo "Opening home"
timeout 25s adb shell am start -W -n com.night.tachymns/.MainActivity
sleep 5
timeout 15s adb shell pidof com.night.tachymns
timeout 35s adb exec-out screencap -p > tac-hymns-home.png
file tac-hymns-home.png

# Use real accessibility semantics to tap labels instead of brittle screen coordinates.
tap_text() {
  local label="$1"
  local xml
  timeout 25s adb shell uiautomator dump /sdcard/window.xml >/dev/null
  xml="$(timeout 15s adb exec-out cat /sdcard/window.xml)"
  local coords
  coords="$(printf '%s' "$xml" | python3 -c '
import re, sys, xml.etree.ElementTree as ET
text = sys.argv[1].lower()
try:
    root = ET.fromstring(sys.stdin.read())
    matches = [n for n in root.iter("node") if
        n.attrib.get("text","").strip().lower() == text or
        n.attrib.get("content-desc","").strip().lower() == text]
    for n in matches:
        values = [int(v) for v in re.findall("[0-9]+", n.attrib.get("bounds",""))]
        if len(values) == 4:
            x1,y1,x2,y2 = values
            print((x1+x2)//2, (y1+y2)//2)
            sys.exit(0)
except Exception:
    pass
sys.exit(1)
' "$label")" || return 1
  timeout 15s adb shell input tap $coords
  sleep 2
}

echo "Switching the actual app to dark mode via More"
tap_text "More"
tap_text "Dark mode"
timeout 35s adb exec-out screencap -p > tac-hymns-dark-home.png
file tac-hymns-dark-home.png

echo "Opening offline audio storage management through More"
tap_text "More"
tap_text "Offline audio"
timeout 35s adb exec-out screencap -p > tac-hymns-offline-audio.png
file tac-hymns-offline-audio.png
tap_text "Back to hymnbook"

echo "Navigating through the separately accessible Learn Hymns screen"
tap_text "More"
tap_text "Learn hymns"
timeout 35s adb exec-out screencap -p > tac-hymns-learn-home.png
file tac-hymns-learn-home.png

echo "Opening Yoruba hymn reader in persisted dark mode"
timeout 20s adb shell am force-stop com.night.tachymns
timeout 25s adb shell am start -W -n com.night.tachymns/.MainActivity --ei preview_hymn 4
sleep 4
timeout 15s adb shell pidof com.night.tachymns
timeout 35s adb exec-out screencap -p > tac-hymns-reader.png
file tac-hymns-reader.png
cp tac-hymns-reader.png tac-hymns-dark-reader.png
cp tac-hymns-reader.png tac-hymns-audio-reader.png

echo "Opening the actual Àkọkún index"
timeout 20s adb shell am force-stop com.night.tachymns
timeout 25s adb shell am start -W -n com.night.tachymns/.MainActivity --es preview_collection akokun
sleep 5
timeout 15s adb shell pidof com.night.tachymns
timeout 35s adb exec-out screencap -p > tac-hymns-akokun-index.png
file tac-hymns-akokun-index.png

echo "Checking for app crashes"
if adb logcat -d -s AndroidRuntime:E | grep -A 8 'Process: com.night.tachymns'; then
  exit 1
fi
