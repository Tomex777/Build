#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/slumber-088/app/build/outputs/apk/debug/app-debug.apk}"
OUT=/tmp/slumber-product-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"

bash .github/scripts/validate-slumber-product.sh "$APK"

function dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/slumber-final.xml >/dev/null 2>&1 || true
  adb pull /sdcard/slumber-final.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

function ui_has() {
  local needle="$1"
  dump_ui probe-final
  python3 - "$OUT/probe-final.xml" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
needle = sys.argv[2]
for node in root.iter("node"):
    a = node.attrib
    if a.get("text", "").strip() == needle or a.get("content-desc", "").strip() == needle:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

function wait_for() {
  local needle="$1"; local seconds="${2:-30}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$needle"; then return 0; fi
    sleep 1
  done
  adb exec-out screencap -p > "$OUT/final-wait-failure.png" || true
  adb logcat -d -t 3000 > "$OUT/final-wait-failure-logcat.txt" || true
  echo "Timed out waiting for final-proof UI: $needle" >&2
  cat "$OUT/probe-final.xml" >&2 || true
  return 1
}

function coords_for() {
  local needle="$1"
  dump_ui coords-final
  python3 - "$OUT/coords-final.xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
needle = sys.argv[2]
for node in root.iter("node"):
    a = node.attrib
    if a.get("text", "").strip() == needle or a.get("content-desc", "").strip() == needle:
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

function tap_ui() {
  local needle="$1"
  local xy=""
  for _ in $(seq 1 12); do
    xy="$(coords_for "$needle" 2>/dev/null || true)"
    if [ -n "$xy" ]; then
      adb shell input tap $xy
      return 0
    fi
    sleep 1
  done
  echo "Could not tap final-proof UI target: $needle" >&2
  return 1
}

function capture() {
  local name="$1"
  dump_ui "$name"
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
}

function assert_orientation() {
  local name="$1"; local wanted="$2"
  python3 - "$OUT/$name.png" "$wanted" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
w,h=struct.unpack('>II',b[16:24])
wanted=sys.argv[2]
if wanted == 'landscape': assert w > h, (w,h)
else: assert h > w, (w,h)
print(f"{sys.argv[1]}: {w}x{h} {wanted}")
PY
}

function background_to_settings() {
  local name="$1"
  local before
  before="$(adb shell pidof "$PKG" | tr -d '\r')"
  test -n "$before"
  adb shell am start -W -a android.settings.SETTINGS > "$OUT/$name-settings-start.txt"
  sleep 1
  local during
  during="$(adb shell pidof "$PKG" | tr -d '\r')"
  test -n "$during"
  test "$during" = "$before"
  adb shell dumpsys activity activities > "$OUT/$name-background-activities.txt"
  if grep -E "mResumedActivity.*$PKG|topResumedActivity.*$PKG|ResumedActivity.*$PKG" "$OUT/$name-background-activities.txt"; then
    echo "Slumber never actually left the foreground during $name proof" >&2
    exit 1
  fi
}

wait_for "Practice" 20
tap_ui "Falling notes"
wait_for "FALLING NOTES" 35
wait_for "Ready to play?" 15
tap_ui "Start"
sleep 0.35
background_to_settings play
sleep 6

# Prove the game did not complete while Settings is still the resumed activity.
# This avoids counting slow landscape/orientation restoration as background time.
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs-after-background.xml"
python3 - "$OUT/prefs-after-background.xml" <<'PY'
import html,json,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
node=next(x for x in root if x.attrib.get("name")=="play_progress_v1")
data=json.loads(html.unescape(node.text or "{}"))["first-melody"]
assert data["completedRuns"] == 1, data
print("Settings-background run count stayed frozen", data)
PY
adb shell dumpsys activity activities > "$OUT/play-still-backgrounded-activities.txt"
if grep -E "mResumedActivity.*$PKG|topResumedActivity.*$PKG|ResumedActivity.*$PKG" "$OUT/play-still-backgrounded-activities.txt"; then
  echo "Slumber resumed before the background-state assertion" >&2
  exit 1
fi

adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "FALLING NOTES" 30
wait_for "Restart" 10
capture play-returned-after-background
assert_orientation play-returned-after-background landscape

wait_for "Run complete" 10
capture play-complete-after-resume
assert_orientation play-complete-after-resume landscape
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs-after-resume-complete.xml"
python3 - "$OUT/prefs-after-resume-complete.xml" <<'PY'
import html,json,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
node=next(x for x in root if x.attrib.get("name")=="play_progress_v1")
data=json.loads(html.unescape(node.text or "{}"))["first-melody"]
assert data["completedRuns"] == 2, data
print("foreground resume completed play normally", data)
PY

tap_ui "Back"
wait_for "Practice" 30
capture practice-after-background
assert_orientation practice-after-background portrait

tap_ui "Continue practice"
wait_for "88 keys" 35
capture piano-final-regression
assert_orientation piano-final-regression landscape

adb exec-out screencap -p > "$OUT/final-size.png"
read -r W H <<<"$(python3 - "$OUT/final-size.png" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
print(*struct.unpack('>II',b[16:24]))
PY
)"
Y=$((H*87/100))
adb shell input swipe $((W*8/100)) "$Y" $((W*24/100)) "$Y" 550 >/dev/null &
P1=$!
adb shell input swipe $((W*34/100)) "$Y" $((W*50/100)) "$Y" 550 >/dev/null &
P2=$!
wait "$P1"
wait "$P2"
sleep 1
adb shell pidof "$PKG" >/dev/null

background_to_settings piano
sleep 2
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "88 keys" 30
capture piano-resumed
assert_orientation piano-resumed landscape

tap_ui "Back"
wait_for "Practice" 30
capture practice-final
assert_orientation practice-final portrait

# In-app Settings visual QA + real preference persistence.
tap_ui "Settings"
wait_for "Make Slumber yours" 20
wait_for "Sounds" 5
wait_for "AI Teacher" 5
capture settings
assert_orientation settings portrait

tap_ui "Dark"
sleep 1
capture settings-dark
assert_orientation settings-dark portrait
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs-settings-dark.xml"
python3 - "$OUT/prefs-settings-dark.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
node=next(x for x in root if x.attrib.get("name")=="theme_mode")
assert (node.text or "").strip()=="DARK", node.text
print("dark theme preference persisted in-process")
PY

tap_ui "Sounds"
wait_for "SETTINGS · SOUNDS" 20
wait_for "Pianos & instruments" 5
capture settings-sounds
assert_orientation settings-sounds portrait
tap_ui "Back"
wait_for "Make Slumber yours" 20

adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 30
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs-settings-relaunch.xml"
python3 - "$OUT/prefs-settings-relaunch.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
node=next(x for x in root if x.attrib.get("name")=="theme_mode")
assert (node.text or "").strip()=="DARK", node.text
print("dark theme survived process restart")
PY
capture practice-dark-relaunch
assert_orientation practice-dark-relaunch portrait

# Home -> return must preserve the same live process and restore a usable Practice screen.
HOME_PID_BEFORE="$(adb shell pidof "$PKG" | tr -d '\r')"
test -n "$HOME_PID_BEFORE"
adb shell input keyevent KEYCODE_HOME
sleep 2
HOME_PID_DURING="$(adb shell pidof "$PKG" | tr -d '\r')"
test "$HOME_PID_DURING" = "$HOME_PID_BEFORE"
adb shell dumpsys activity activities > "$OUT/home-background-activities.txt"
if grep -E "mResumedActivity.*$PKG|topResumedActivity.*$PKG|ResumedActivity.*$PKG" "$OUT/home-background-activities.txt"; then
  echo "Slumber never actually left the foreground during Home proof" >&2
  exit 1
fi
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 30
HOME_PID_AFTER="$(adb shell pidof "$PKG" | tr -d '\r')"
test "$HOME_PID_AFTER" = "$HOME_PID_BEFORE"
capture practice-home-resumed
assert_orientation practice-home-resumed portrait

adb logcat -d -t 7000 > "$OUT/final-lifecycle-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/final-lifecycle-logcat.txt"; then
  echo 'Slumber crashed during final lifecycle/input validation' >&2
  exit 1
fi

cat >> "$OUT/GREEN.txt" <<'TXT'
SDK CONTRACT = GREEN (compile 36 / target 36 / min 26)
PLAY BACKGROUND PAUSE = GREEN
PIANO BACKGROUND/FOREGROUND = GREEN
RESTORED 3D KEYBOARD REGRESSION = GREEN
CONCURRENT TOUCH STRESS = GREEN
FINAL LIFECYCLE POLISH = GREEN
SETTINGS VISUAL QA = GREEN
DARK THEME PERSISTENCE = GREEN
SOUNDS NAVIGATION = GREEN
HOME BACKGROUND/FOREGROUND = GREEN
TXT
cat "$OUT/GREEN.txt"
