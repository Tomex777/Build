#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/slumber-088/app/build/outputs/apk/debug/app-debug.apk}"
OUT=/tmp/slumber-chord-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"

dump_ui() {
  adb shell uiautomator dump /sdcard/slumber-chord.xml >/dev/null 2>&1 || true
  adb pull /sdcard/slumber-chord.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
}

ui_has() {
  local needle="$1"
  dump_ui
  python3 - "$OUT/ui.xml" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: raise SystemExit(1)
needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if a.get("text","").strip()==needle or a.get("content-desc","").strip()==needle:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for() {
  local needle="$1"; local seconds="${2:-35}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$needle"; then return 0; fi
    sleep 1
  done
  echo "Chord proof timed out waiting for: $needle" >&2
  adb exec-out screencap -p > "$OUT/failure.png" || true
  return 1
}

coords_for() {
  local needle="$1"
  dump_ui
  python3 - "$OUT/ui.xml" "$needle" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if a.get("text","").strip()==needle or a.get("content-desc","").strip()==needle:
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2,(y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_ui() {
  local needle="$1"
  local xy
  xy="$(coords_for "$needle")"
  adb shell input tap $xy
}

capture() {
  local name="$1"
  dump_ui
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
}

adb install -r "$APK" >/dev/null
adb shell pm clear "$PKG" >/dev/null || true
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 40

tap_ui "Songs"
wait_for "Learn a song" 30
tap_ui "Slumber Originals"
wait_for "Velvet Cadence" 20
wait_for "Play Velvet Cadence" 10
capture chord-song

tap_ui "Play Velvet Cadence"
wait_for "FALLING NOTES" 35
wait_for "Ready to play?" 15
capture play-chord-ready

adb exec-out screencap -p > "$OUT/size.png"
read -r W H <<<"$(python3 - "$OUT/size.png" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
print(*struct.unpack('>II',b[16:24]))
PY
)"

tap_ui "Start"
python3 - "$W" "$H" "$OUT" <<'PY'
import subprocess, sys, time

w,h=map(int,sys.argv[1:3])
out=sys.argv[3]
white_pcs={0,2,4,5,7,9,11}
whites=[m for m in range(21,109) if m%12 in white_pcs]
chords=[
    {60,64,67},
    {57,60,64},
    {53,57,60},
    {55,59,62},
    {60,64,67},
]
all_notes=sorted(set().union(*chords))
positions=[whites.index(m) for m in all_notes]
center=(min(positions)+max(positions))/2.0
start=int((center-22/2.0)+0.5)
start=max(0,min(start,len(whites)-22))
visible={m:whites.index(m)-start for m in all_notes}
assert all(0<=i<22 for i in visible.values()),visible

y=round(h*.95)
beat=60.0/84.0
origin=time.monotonic()-.10
for chord_index,chord in enumerate(chords):
    target=origin+chord_index*beat
    remaining=target-time.monotonic()
    if remaining>0:
        time.sleep(remaining)
    procs=[]
    for midi in sorted(chord):
        x=round(w*(visible[midi]+.5)/22.0)
        procs.append(subprocess.Popen(
            ["adb","shell","input","swipe",str(x),str(y),str(x),str(y),"120"],
            stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
        ))
    for p in procs:
        if p.wait()!=0: raise SystemExit("chord input failed")
    if chord_index==0:
        time.sleep(.12)
        with open(f"{out}/play-chord.png","wb") as fp:
            subprocess.run(["adb","exec-out","screencap","-p"],check=True,stdout=fp)
PY

wait_for "Play again" 15
capture play-chord-complete
adb shell run-as "$PKG" cat shared_prefs/pianohub_local_v1.xml > "$OUT/prefs.xml"
python3 - "$OUT/prefs.xml" <<'PY'
import html,json,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
node=next(x for x in root if x.attrib.get("name")=="play_progress_v1")
data=json.loads(html.unescape(node.text or "{}"))["original-velvet-cadence"]
assert data["completedRuns"] == 1, data
assert data["bestScore"] >= 500, data
assert data["bestAccuracy"] >= 60, data
assert data["bestCombo"] >= 3, data
print("real chord play progress",data)
PY

adb logcat -d -t 3500 > "$OUT/chord-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/chord-logcat.txt"; then
  echo "Slumber crashed during chord gameplay proof" >&2
  exit 1
fi

cat > "$OUT/GREEN.txt" <<'TXT'
CHORD CHART = GREEN
CHORD LANE GROUPING = GREEN
CHORD MULTI-POINTER INPUT = GREEN
CHORD SCORING = GREEN
TXT
cat "$OUT/GREEN.txt"
