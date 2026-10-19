#!/usr/bin/env bash
set -euo pipefail
mkdir -p SoundAtlas/qa
APK=SoundAtlas/app/build/outputs/apk/debug/app-debug.apk
adb wait-for-device
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb install -r "$APK"
adb shell am start -W -n com.night.soundatlas/.MainActivity
sleep 6
W=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x[0-9]*/\1/p' | head -1)
H=$(adb shell wm size | sed -n 's/.*x\([0-9]*\)/\1/p' | head -1)
D=$(adb shell wm density | sed -n 's/.*: \([0-9]*\)/\1/p' | head -1)
test -n "$W" && test -n "$H" && test -n "$D"
echo "Actual emulator: $W x $H, density=$D"
adb shell uiautomator dump /data/local/tmp/atlas-hierarchy.xml >/dev/null 2>&1 || true
if adb shell cat /data/local/tmp/atlas-hierarchy.xml 2>/dev/null | grep -q 'text="Got it"'; then
  adb shell input tap $((W*84/100)) $((H/4))
  sleep 3
fi
capture() {
  local target="$1"
  local output="SoundAtlas/qa/$target"
  for attempt in 1 2 3 4; do
    adb exec-out screencap -p > "$output"
    if test "$(wc -c < "$output")" -gt 5000 &&
       python3 SoundAtlas/tools/png_modal_guard.py "$output"; then
      echo "UI_VISIBLE_SCREENSHOT: $target"
      return 0
    fi
    echo "System dialog blocking app: waiting and retrying $target ($attempt)"
    adb shell input tap $((W * 39 / 100)) $((H * 54 / 100)) || true
    sleep 5
  done
  echo "QA_FAIL: The real Sound Atlas screen stayed obscured by system UI"
  return 1
}
adb shell pidof com.night.soundatlas
capture 01-cathedral.png
# The custom Canvas docks bottom actions: World, Sounds, Walls, Rooms, Explore.
# All taps are calculated from current physical dimensions and measured density.
tap_dp() {
  adb shell input tap "$(( $1 * W / 100 ))" "$(( H - $2 * D / 160 ))"
  sleep 2
}
tap_desc() {
  local label="$1"
  adb shell uiautomator dump /data/local/tmp/atlas-hierarchy.xml >/dev/null
  adb shell cat /data/local/tmp/atlas-hierarchy.xml > SoundAtlas/qa/hierarchy-current.xml
  python3 - "$label" SoundAtlas/qa/hierarchy-current.xml <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as ET
label,path=sys.argv[1:]
root=ET.parse(path).getroot()
matches=[n for n in root.iter("node") if n.attrib.get("content-desc")==label]
if not matches:
    found=[n.attrib.get("content-desc","") for n in root.iter("node")
           if n.attrib.get("content-desc")]
    raise SystemExit(f"ACCESSIBLE_CONTROL_NOT_FOUND {label}: {found[:35]}")
bounds=list(map(int,re.findall(r"\d+",matches[-1].attrib.get("bounds",""))))
assert len(bounds)==4,bounds
x=(bounds[0]+bounds[2])//2
y=(bounds[1]+bounds[3])//2
subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True,timeout=20)
print("REAL_TALKBACK_CONTROL_ACTIVATED",label,x,y,flush=True)
PY
  sleep 2
}
assert_state() {
  local expected="$1"
  adb shell uiautomator dump /data/local/tmp/atlas-hierarchy.xml >/dev/null
  adb shell cat /data/local/tmp/atlas-hierarchy.xml > SoundAtlas/qa/hierarchy-current.xml
  if ! grep -Fq "$expected" SoundAtlas/qa/hierarchy-current.xml; then
    echo "UI_STATE_MISMATCH: expected '$expected'"; exit 1
  fi
  echo "ATLAS_UI_STATE_VERIFIED: $expected"
}
assert_state 'World Storm Cathedral. Panel NONE'
tap_desc 'WORLD'
capture 02-world-library.png
assert_state 'Panel LIBRARY'
# First-row right card, within world-library sheet (370dp tall).
tap_desc 'Open Ancient Forest'
capture 03-selected-world.png
assert_state 'World Ancient Forest. Panel NONE'
tap_desc 'SOUNDS'
capture 04-sound-library.png
assert_state 'Panel SOUNDS page 1'
# Verify that newly licensed sounds on the second page can actually be added.
tap_desc 'NEXT SOUNDS'
assert_state 'Panel SOUNDS page 2'
tap_desc 'Add Ocean surf'
capture 05-sound-inspector.png
assert_state 'Panel INSPECT'
# Close inspector and enter Explore mode.
tap_desc 'Close panel'
tap_desc 'EXPLORE'
capture 06-explore.png
assert_state 'Mode EXPLORE'
# Enter atmospheric editor from the header after returning to edit.
tap_desc 'EDIT'
tap_desc 'Edit rain, wind and storm'
capture 07-atmosphere.png
assert_state 'Panel ATMOSPHERE'
adb shell pidof com.night.soundatlas
adb logcat -d -s AndroidRuntime:E > SoundAtlas/qa/crashes.txt
if grep -A 8 'FATAL EXCEPTION' SoundAtlas/qa/crashes.txt | grep -q 'com.night.soundatlas'; then
  echo "Sound Atlas runtime crash detected"
  exit 1
fi
echo "SOUND_ATLAS_EMULATOR_QA_PASSED"
