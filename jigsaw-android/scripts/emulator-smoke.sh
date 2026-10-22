#!/usr/bin/env bash
set -euo pipefail
mkdir -p jigsaw-android/qa-screenshots
adb install -r apk/app-debug.apk
adb shell wm size 390x844
adb shell wm density 420
adb shell am start -W -n com.nice.jigsaw/.MainActivity
sleep 5
adb shell pidof com.nice.jigsaw >/dev/null
adb exec-out screencap -p > jigsaw-android/qa-screenshots/01-starter-390.png

# Read actual Compose accessibility node bounds: don't guess tap coordinates.
tap_label() {
  local label="$1"
  adb shell uiautomator dump /sdcard/jigsaw-ui.xml >/dev/null
  adb exec-out cat /sdcard/jigsaw-ui.xml > /tmp/jigsaw-ui.xml
  local xy
  xy=$(python3 - "$label" <<'PY'
import re,sys,xml.etree.ElementTree as ET
label=sys.argv[1].casefold()
root=ET.parse("/tmp/jigsaw-ui.xml").getroot()
for item in root.iter("node"):
    txt=(item.get("text","")+" "+item.get("content-desc","")).casefold()
    if label in txt:
        match=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",item.get("bounds",""))
        if match:
            x1,y1,x2,y2=map(int,match.groups())
            print((x1+x2)//2,(y1+y2)//2)
            sys.exit(0)
raise SystemExit("No visible tappable accessibility node matches "+repr(label))
PY
)
  read -r x y <<< "$xy"
  adb shell input tap "$x" "$y"
  sleep 2
}
tap_label "New puzzle"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/02-difficulty-390.png
tap_label "48"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/03-pieces-48-390.png
tap_label "Hint"
adb shell run-as com.nice.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-progress.json
python3 - <<'PY'
import json
saved=json.load(open("/tmp/jigsaw-progress.json"))
assert saved["cols"]==8 and saved["rows"]==6, saved
assert saved["hints"]==2, saved
assert sum(bool(p[2]) for p in saved["pieces"])==1, saved
print("PASS: 48 pieces; hint positioned and persisted; 2 hints remain")
PY
tap_label "New puzzle"
tap_label "144"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/04-pieces-144-390.png
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n com.nice.jigsaw/.MainActivity
sleep 3
adb shell run-as com.nice.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-restored.json
python3 - <<'PY'
import json
saved=json.load(open("/tmp/jigsaw-restored.json"))
assert saved["cols"]==12 and saved["rows"]==12, saved
assert len(saved["pieces"])==144, saved
print("PASS: native 144-piece board persists through background/relaunch")
PY
adb exec-out screencap -p > jigsaw-android/qa-screenshots/05-restored-144-390.png
adb shell wm size 360x740
sleep 2
adb exec-out screencap -p > jigsaw-android/qa-screenshots/06-144-360.png
echo "Native standalone Jigsaw device smoke: PASS"
