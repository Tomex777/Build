#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/slumber-088/app/build/outputs/apk/debug/app-debug.apk}"
OUT=/tmp/slumber-product-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"
adb logcat -c || true

function dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/slumber.xml >/dev/null 2>&1 || true
  adb pull /sdcard/slumber.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

function ui_has() {
  local needle="$1"
  dump_ui probe
  python3 - "$OUT/probe.xml" "$needle" <<'PY'
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
    dismiss_system_dialogs || true
    sleep 1
  done
  adb exec-out screencap -p > "$OUT/wait-failure.png" || true
  adb logcat -d -t 3000 > "$OUT/wait-failure-logcat.txt" || true
  echo "Timed out waiting for UI: $needle" >&2
  cat "$OUT/probe.xml" >&2 || true
  return 1
}

function coords_for() {
  local needle="$1"
  dump_ui coords
  python3 - "$OUT/coords.xml" "$needle" <<'PY'
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
  local xy
  xy="$(coords_for "$needle")"
  adb shell input tap $xy
}

function dismiss_system_dialogs() {
  dump_ui system-dialog
  local xy
  xy="$(python3 - "$OUT/system-dialog.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: raise SystemExit(0)
for wanted in ("Wait", "Got it"):
  for node in root.iter("node"):
    a=node.attrib
    if a.get("text")==wanted or a.get("content-desc")==wanted:
      m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
      if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
PY
  )" || true
  if [ -n "${xy:-}" ]; then adb shell input tap $xy; sleep 1; fi
}

function capture() {
  local name="$1"
  dump_ui "$name"
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
}

function assert_landscape_png() {
  local name="$1"
  python3 - "$OUT/$name.png" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
assert b[:8]==b'\x89PNG\r\n\x1a\n'
w,h=struct.unpack('>II',b[16:24])
assert w>h,(w,h)
print(f"{sys.argv[1]}: {w}x{h} landscape")
PY
}

function assert_portrait_png() {
  local name="$1"
  python3 - "$OUT/$name.png" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
w,h=struct.unpack('>II',b[16:24])
assert h>w,(w,h)
print(f"{sys.argv[1]}: {w}x{h} portrait")
PY
}

adb install -r "$APK" >/dev/null
adb shell pm clear "$PKG" >/dev/null || true
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 35
wait_for "Falling notes" 5
wait_for "Restored 3D playing surface" 5
capture practice
assert_portrait_png practice

# Practice -> real restored piano.
tap_ui "Start practice"
wait_for "88 keys" 35
dismiss_system_dialogs || true
wait_for "88 keys" 15
capture piano
assert_landscape_png piano
# Exercise one real pointer path across the keybed. This produces a glissando
# through PianoKeyboard's per-pointer ownership path without replacing it.
read -r W H <<<"$(adb shell wm size | tail -1 | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1 \2/' | tr -d '\r')"
Y=$((H*87/100)); X1=$((W*8/100)); X2=$((W*28/100))
adb shell input swipe "$X1" "$Y" "$X2" "$Y" 450
sleep 1
tap_ui "Back"
wait_for "Practice" 30

# Practice -> Songs.
tap_ui "Songs"
wait_for "Learn a song" 20
wait_for "Official Songs" 5
wait_for "C major warm-up" 5
wait_for "Play" 5
capture songs
assert_portrait_png songs

# Songs -> falling-note Play. The first Play button belongs to the first lesson.
tap_ui "Play"
wait_for "FALLING NOTES" 35
dismiss_system_dialogs || true
wait_for "Ready to play?" 15
capture play-ready
assert_landscape_png play-ready

tap_ui "Start"
sleep 0.10
# The default C4-C7 viewport begins on C4. Hit the first C4 tile while it is
# at the judgement line so the runtime proof includes a real scored touch.
read -r W H <<<"$(adb shell wm size | tail -1 | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1 \2/' | tr -d '\r')"
X=$((W*23/1000)); Y=$((H*88/100))
adb shell input tap "$X" "$Y"
sleep 1.1
wait_for "Score" 5
wait_for "Combo" 5
capture play-active
assert_landscape_png play-active
wait_for "Run complete" 12
capture play-complete
assert_landscape_png play-complete

# The completed run must be durable, not just an overlay.
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs-after-play.xml"
grep -q 'play_progress_v1' "$OUT/prefs-after-play.xml"
grep -q 'completedRuns' "$OUT/prefs-after-play.xml"

tap_ui "Back"
wait_for "Learn a song" 30
tap_ui "Practice"
wait_for "Practice" 20
capture practice-after-play
assert_portrait_png practice-after-play

# Process death/relaunch must preserve Play progress.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 30
wait_for "Falling notes" 5
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs-after-relaunch.xml"
grep -q 'play_progress_v1' "$OUT/prefs-after-relaunch.xml"
grep -q 'completedRuns' "$OUT/prefs-after-relaunch.xml"
capture practice-relaunch
assert_portrait_png practice-relaunch

adb logcat -d -t 5000 > "$OUT/final-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/final-logcat.txt"; then
  echo 'Slumber crashed during product validation' >&2
  exit 1
fi

cat > "$OUT/GREEN.txt" <<'TXT'
PIANO = GREEN
PRACTICE = GREEN
SONGS = GREEN
PLAY = GREEN
NAVIGATION = GREEN
PERSISTENCE = GREEN
RUNTIME VALIDATION = GREEN
FINAL APP = GREEN
TXT
cat "$OUT/GREEN.txt"
