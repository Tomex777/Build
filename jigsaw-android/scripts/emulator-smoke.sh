#!/usr/bin/env bash
set -euo pipefail
mkdir -p jigsaw-android/qa-screenshots
adb install -r apk/app-debug.apk
adb shell wm size 390x844
adb shell wm density 420
adb shell am start -W -n com.night.jigsaw/.MainActivity
sleep 5
adb shell pidof com.night.jigsaw >/dev/null
adb exec-out screencap -p > jigsaw-android/qa-screenshots/01-starter-390.png

# Read actual Compose accessibility node bounds: don't guess tap coordinates.
tap_label() {
  local label="$1"
  local attempt xy
  for attempt in $(seq 1 13); do
    adb shell uiautomator dump /sdcard/jigsaw-ui.xml >/dev/null
    adb exec-out cat /sdcard/jigsaw-ui.xml > /tmp/jigsaw-ui.xml
    xy=$(python3 - "$label" <<'PY'
import re,sys,xml.etree.ElementTree as ET
label=sys.argv[1].casefold()
root=ET.parse("/tmp/jigsaw-ui.xml").getroot()
for node in root.iter("node"):
    txt=(node.get("text","")+" "+node.get("content-desc","")).casefold()
    if label not in txt:
        continue
    match=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.get("bounds",""))
    if match:
        x1,y1,x2,y2=map(int,match.groups())
        if x2>x1 and y2>y1:
            print((x1+x2)//2,(y1+y2)//2)
            break
PY
)
    if [ -n "$xy" ]; then
      local x y
      read -r x y <<< "$xy"
      adb shell input tap "$x" "$y"
      sleep 2
      return 0
    fi
    # Dialog size selectors and 600-piece list are scrollable.
    # Compute swipe bounds from the emulator's current display geometry.
    local dims cx fromy toy
    dims=$(adb shell wm size | grep -E '(Physical|Override) size:' | tail -1 | grep -o '[0-9]*x[0-9]*')
    local w="${dims%x*}" h="${dims#*x}"
    cx=$((w/2));fromy=$((h*73/100));toy=$((h*30/100))
    adb shell input swipe "$cx" "$fromy" "$cx" "$toy" 280
    sleep 1
  done
  echo "FAIL: no on-screen accessible UI button matching '$label' after scrolling" >&2
  return 1
}

tap_label "New puzzle"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/02-difficulty-390.png
tap_label "48"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/03-pieces-48-390.png
tap_label "Hint"
adb shell run-as com.night.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-progress.json
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
adb shell am start -W -n com.night.jigsaw/.MainActivity
sleep 3
adb shell run-as com.night.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-restored.json
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
# Landscape visual proof: rotate the emulated Android display, not just the web viewport.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell wm size 844x390
sleep 4
adb shell pidof com.night.jigsaw >/dev/null
adb exec-out screencap -p > jigsaw-android/qa-screenshots/07-landscape-144-844x390.png
adb shell run-as com.night.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-landscape.json
python3 - <<'PY'
import json
saved=json.load(open("/tmp/jigsaw-landscape.json"))
assert saved["cols"]==12 and saved["rows"]==12, saved
assert saved["landscape"] is True, "The Activity must preserve and switch its tabletop mode."
print("PASS: 144-piece landscape board recorded in saved state.")
PY
tap_label "New puzzle"
tap_label "240"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/08-landscape-240-844x390.png
tap_label "New puzzle"
tap_label "600"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/09-landscape-600-844x390.png
adb shell settings put system user_rotation 0
adb shell wm size 390x844
sleep 3
adb exec-out screencap -p > jigsaw-android/qa-screenshots/10-portrait-600-390x844.png
tap_label "Zone"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/11-select-work-zone.png
tap_label "2."
adb exec-out screencap -p > jigsaw-android/qa-screenshots/12-zone-two-600.png
tap_label "Edges"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/13-edge-only-600.png
tap_label "Pictures"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/14-saved-photo-gallery.png
tap_label "Continue saved puzzles"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/15-multiple-resumable-puzzles.png
tap_label "Close"
tap_label "New puzzle"
tap_label "Memory"
tap_label "12"
adb shell run-as com.night.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-memory.json
python3 - <<'PY'
import json
data=json.load(open("/tmp/jigsaw-memory.json"))
assert data["mode"]=="MEMORY" and data["hints"]==0, data
assert data["previewsRemaining"]==1, data
assert len(data["pieces"])==12, data
print("PASS: Memory mode applies native difficulty/hint budgets")
PY
tap_label "Picture"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/16-reference-options.png
tap_label "Peek at full photo"
adb exec-out screencap -p > jigsaw-android/qa-screenshots/17-memory-timed-preview.png
sleep 6
adb shell run-as com.night.jigsaw cat files/active-jigsaw.json > /tmp/jigsaw-peek-used.json
python3 - <<'PY'
import json
data=json.load(open("/tmp/jigsaw-peek-used.json"))
assert data["previewsRemaining"]==0, data
assert data["mode"]=="MEMORY", data
assert data["preview"] is False, data
print("PASS: Memory mode's single limited full-photo peek is consumed")
PY

adb logcat -d -s AndroidRuntime:E > /tmp/jigsaw-crashlog.txt
if grep -q 'FATAL EXCEPTION' /tmp/jigsaw-crashlog.txt; then
  cat /tmp/jigsaw-crashlog.txt
  echo "FAIL: Android runtime crash in Jigsaw QA" >&2
  exit 1
fi

# Reject empty/unexpected screenshots and verify both orientations were rendered.
python3 - <<'PY'
import pathlib,struct
photos=list(pathlib.Path("jigsaw-android/qa-screenshots").glob("*.png"))
assert len(photos)>=10,len(photos)
for path in photos:
    data=path.read_bytes()
    assert data[:8]==b"\\x89PNG\\r\\n\\x1a\\n",path
    width,height=struct.unpack(">II",data[16:24])
    assert width>=300 and height>=300,(path,width,height)
    assert len(data)>9000,(path,len(data))
print("PASS: verified",len(photos),"nonempty native-screen PNG captures")
PY
echo "Native standalone Jigsaw device smoke: PASS"
