#!/usr/bin/env bash
set -euo pipefail
DIR="night-browser/screenshots/omni-ui"
mkdir -p "$DIR"
APK="night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk"
test -s "$APK"
adb install -r "$APK"
adb shell wm size 1080x1920
adb shell wm density 420
adb shell am start -n com.nice.browser/.MainActivity
sleep 15

capture() {
  sleep 2
  adb shell screencap -p /sdcard/night-capture.png
  adb pull /sdcard/night-capture.png "$DIR/$1" >/dev/null
  test -s "$DIR/$1"
}
dump_ui() {
  adb shell uiautomator dump /sdcard/night-ui.xml >/dev/null 2>&1
  adb pull /sdcard/night-ui.xml "$DIR/hierarchy.xml" >/dev/null
}
tap_ui() {
  local match="$1"
  dump_ui
  local xy
  xy=$(python3 - "$DIR/hierarchy.xml" "$match" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
    for element in root.iter('node'):
        if element.get('content-desc')==sys.argv[2] or element.get('text')==sys.argv[2]:
            numbers=list(map(int,re.findall(r'\d+',element.get('bounds',''))))
            if len(numbers)==4:
                print((numbers[0]+numbers[2])//2, (numbers[1]+numbers[3])//2)
                break
except Exception as exc:
    print(exc,file=sys.stderr)
PY
  )
  if [ -z "$xy" ]; then
    echo "Missing accessible control: $match" >&2
    return 1
  fi
  adb shell input tap $xy
  sleep 2
}
capture "01-home.png"
# Navigate from the REAL custom homepage input, then capture Gecko-rendered web content.
tap_ui "Search or enter address"
adb shell input text 'example.com'
adb shell input keyevent 66
sleep 12
capture "02-web.png"
tap_ui "Tabs"
capture "03-chrome-tab-grid.png"
tap_ui "Home"
tap_ui "More"
capture "04-omni-style-menu.png"
tap_ui "Journeys"
capture "05-journeys.png"
tap_ui "Home"
tap_ui "More"
tap_ui "Library"
capture "06-library.png"
tap_ui "More"
tap_ui "Time Machine"
capture "07-time-machine.png"
adb shell dumpsys activity activities > "$DIR/activity.txt"
adb logcat -d -t 10000 | grep -E 'FATAL EXCEPTION|AndroidRuntime|com.nice.browser' | tail -350 > "$DIR/logcat.txt" || true
if grep -E 'FATAL EXCEPTION.*|Process: com.nice.browser' "$DIR/logcat.txt"; then
  echo 'Detected browser crash' >&2
  exit 1
fi
test -s "$DIR/03-chrome-tab-grid.png"
