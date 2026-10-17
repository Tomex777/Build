#!/usr/bin/env bash
# Real UI / lifecycle acceptance for the portrait Piano Tiles build.
set -euo pipefail
APK="${1:?APK required}"
OUT="${2:-/tmp/slumber-abr-proof}"
MODE="${3:-full}"
PKG=com.night.pianohub
mkdir -p "$OUT"

dump() {
  adb shell uiautomator dump /sdcard/slumber-abr.xml >/dev/null 2>&1 || true
  adb pull /sdcard/slumber-abr.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
}
coords() {
  dump
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import sys,re,xml.etree.ElementTree as E
try: root=E.parse(sys.argv[1]).getroot()
except Exception: raise SystemExit(1)
for e in root.iter('node'):
 a=e.attrib
 if a.get('text','').strip()==sys.argv[2] or a.get('content-desc','').strip()==sys.argv[2]:
  m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',a.get('bounds',''))
  if m:
   x1,y1,x2,y2=map(int,m.groups());print((x1+x2)//2,(y1+y2)//2);raise SystemExit(0)
raise SystemExit(1)
PY
}
seen() { coords "$1" >/dev/null 2>&1; }
wait_seen() {
  local wanted="$1" timeout="${2:-25}"
  for ((i=0;i<timeout;i++)); do
    if seen "$wanted"; then return 0; fi
    # Android 8 first-entry immersive education and Android 16 launcher dialogs
    # are system-owned; close them before rechecking the real app UI.
    for label in "Got it" "Wait"; do
      if xy="$(coords "$label" 2>/dev/null)"; then adb shell input tap $xy || true; fi
    done
    sleep 1
  done
  echo "Not visible: $wanted" >&2
  dump; cp "$OUT/ui.xml" "$OUT/failure-$(echo "$wanted" | tr ' /' '--').xml" || true
  adb exec-out screencap -p > "$OUT/failure.png" || true
  adb logcat -d -t 3000 > "$OUT/failure-logcat.txt" || true
  exit 1
}
tap() {
  local xy
  xy="$(coords "$1")" || { echo "Cannot tap $1" >&2; exit 1; }
  adb shell input tap $xy
}
shot() {
  local name="$1" kind="$2"
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
  python3 - "$OUT/$name.png" "$kind" <<'PY'
import sys,struct
b=open(sys.argv[1],'rb').read(24)
w,h=struct.unpack('>II',b[16:24]); assert w>0 and h>0
if sys.argv[2]=='portrait':assert h>w,(w,h)
if sys.argv[2]=='landscape':assert w>h,(w,h)
print(sys.argv[1],w,h,sys.argv[2])
PY
  dump
  cp "$OUT/ui.xml" "$OUT/$name.xml" || true
}
get_size() {
  adb exec-out screencap -p > "$OUT/size.png"
  python3 - "$OUT/size.png" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
print(*struct.unpack('>II',b[16:24]))
PY
}

adb wait-for-device
adb install -r "$APK" >/dev/null
adb shell pm clear "$PKG" >/dev/null
adb logcat -c || true
adb shell am start -n "$PKG/.MainActivity" >/dev/null
wait_seen "Practice" 60
wait_seen "Play tiles" 12
wait_seen "Free piano" 12
if seen "Falling notes"; then echo 'Legacy Play entry leaked' >&2;exit 1;fi
shot practice portrait

# Same playable piano remains available; distinct from Play and Learn.
tap "Free piano"
wait_seen "88 keys" 45
shot piano landscape
adb shell input keyevent KEYCODE_BACK
wait_seen "Practice" 35

# True song library, not a decorative menu.
tap "Songs"
wait_seen "Songs" 30
wait_seen "Find a song" 18
shot songs portrait
# Original challenge song is long enough to prove pause/resume without racing.
tap "Find a song"
adb shell input text 'Jingle%sbells'
adb shell input keyevent KEYCODE_BACK || true
wait_seen "Jingle Bells" 20
if seen "Play Jingle Bells"; then tap "Play Jingle Bells"; else tap "Play"; fi
wait_seen "Tap the first tile to start" 45
shot tiles-ready portrait

# The first Jingle Bells note is E4 (MIDI 64), allocated to lane 0.
read -r W H <<<"$(get_size)"
X=$((W/8)); Y=$((H*92/100))
adb shell input tap "$X" "$Y"
wait_seen "Pause game" 15
shot tiles-playing portrait

if [ "$MODE" = "full" ]; then
  tap "Pause game"
  wait_seen "PAUSED" 12
  shot tiles-paused portrait
  sleep 4
  wait_seen "PAUSED" 8
  tap "RESUME"
  wait_seen "PLAY AGAIN" 75
  shot tiles-results portrait
  adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/progress.xml"
  python3 - "$OUT/progress.xml" <<'PY'
import html,json,sys,xml.etree.ElementTree as E
root=E.parse(sys.argv[1]).getroot()
node=next((x for x in root if x.attrib.get('name')=='play_progress_v1'),None)
assert node is not None, 'No persisted Play progress'
record=json.loads(html.unescape(node.text or '{}'))
assert record.get('jingle-bells',{}).get('completedRuns',0)>=1,record
print('Persisted completed Jingle Bells run',record['jingle-bells'])
PY
  tap "Back to songs"
else
  tap "Back to songs"
fi
wait_seen "Songs" 30

tap "Settings"
wait_seen "Settings" 30
wait_seen "Teacher model" 10
shot settings portrait
tap "Teacher model"
wait_seen "Model name" 18
shot custom-inputs portrait
adb shell input keyevent KEYCODE_BACK
wait_seen "Settings" 20

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity" >/dev/null
wait_seen "Practice" 45
shot practice-relaunch portrait
adb logcat -d -t 6000 > "$OUT/logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/logcat.txt";then
 echo "Slumber crashed during ABR acceptance" >&2;exit 1
fi
printf '%s\n' 'ABR PRACTICE DISTINCT ACTIONS PASS' 'ABR PIANO LANDSCAPE PASS' \
  'ABR SONG LIBRARY PASS' 'ABR TILES PORTRAIT PASS' \
  'ABR INPUT DESIGN PASS' 'ABR PROCESS RELAUNCH PASS' > "$OUT/GREEN.txt"
cat "$OUT/GREEN.txt"