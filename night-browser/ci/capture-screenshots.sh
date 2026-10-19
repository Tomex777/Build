#!/usr/bin/env bash
set -uo pipefail
DIR=night-browser/screenshots
mkdir -p "$DIR"
APK=night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk
test -s "$APK" || { echo "Missing x86_64 APK"; exit 1; }
adb install -r "$APK" || exit 1
adb shell wm size 1080x1920
adb shell wm density 420
adb shell am start -n com.night.browser/.MainActivity
sleep 14
adb shell dumpsys activity activities > "$DIR/activity.txt" || true
adb logcat -d -t 3000 | grep -E 'FATAL EXCEPTION|AndroidRuntime|com.night.browser' | tail -250 > "$DIR/logcat.txt" || true
adb shell screencap -p /sdcard/night-screen.png
adb pull /sdcard/night-screen.png "$DIR/01-home.png"

dump_ui() {
  adb shell uiautomator dump /sdcard/night-ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/night-ui.xml "$DIR/ui.xml" >/dev/null 2>&1 || true
}
tap_ui() {
  local match="$1"
  dump_ui
  local xy
  xy=$(python3 - "$DIR/ui.xml" "$match" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try:
  root=ET.parse(sys.argv[1]).getroot()
  for el in root.iter('node'):
    if el.get('content-desc') == sys.argv[2] or el.get('text') == sys.argv[2]:
      b=el.get('bounds','')
      t=list(map(int,re.findall(r'\d+',b)))
      if len(t)==4:
        print((t[0]+t[2])//2,(t[1]+t[3])//2)
        break
except Exception as ex:
  print('UI dump error',ex,file=sys.stderr)
PY
  )
  if [ -z "$xy" ]; then
    echo "UI element not found: $match" >&2
    return 1
  fi
  adb shell input tap $xy
  sleep 2
}
capture() {
  local file="$1"
  sleep 2
  adb shell screencap -p /sdcard/night-screen.png
  adb pull /sdcard/night-screen.png "$DIR/$file"
}
tap_ui "Tabs" && capture "02-tabs.png" || true
tap_ui "Home" || true
tap_ui "More" && tap_ui "Journeys" && capture "03-journeys.png" || true
tap_ui "More" && tap_ui "Library" && capture "04-library.png" || true
tap_ui "More" && tap_ui "Time Machine" && capture "05-time-machine.png" || true
tap_ui "Home" || true

# Search/navigation is driven by a genuine text-field tap and Android input events.
dump_ui
XY=$(python3 - "$DIR/ui.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try:
  root=ET.parse(sys.argv[1]).getroot()
  for el in root.iter('node'):
    if el.get('text')=='Search or enter address':
      t=list(map(int,re.findall(r'\d+',el.get('bounds',''))))
      if len(t)==4:
        print((t[0]+t[2])//2,(t[1]+t[3])//2);break
except: pass
PY
)
if [ -z "$XY" ]; then XY="450 1750"; fi
adb shell input tap $XY
sleep 1
adb shell input text "example.com"
sleep 1
tap_ui "Go" || adb shell input keyevent 66
sleep 10
capture "06-webpage.png"
adb shell dumpsys activity activities >> "$DIR/activity.txt" || true
adb logcat -d -t 5000 | grep -E 'FATAL EXCEPTION|AndroidRuntime|com.night.browser' | tail -250 >> "$DIR/logcat.txt" || true
