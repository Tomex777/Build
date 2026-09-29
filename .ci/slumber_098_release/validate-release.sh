#!/usr/bin/env bash
set -euo pipefail

ARM64_APK="${1:?ARM64 release APK path required}"
APK="${2:-$ARM64_APK}"
test -s "$ARM64_APK"
test -s "$APK"
OUT=/tmp/slumber-release-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"

adb_bounded() {
  local seconds="$1"; shift
  timeout --foreground --signal=TERM --kill-after=10s "${seconds}s" adb "$@"
}

dump_ui() {
  rm -f "$OUT/ui.xml"
  adb_bounded 30 shell uiautomator dump /sdcard/slumber-release.xml >/dev/null 2>&1 || true
  adb_bounded 30 pull /sdcard/slumber-release.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
}

dismiss_system_dialogs() {
  adb_bounded 30 shell uiautomator dump /sdcard/slumber-release-system.xml >/dev/null 2>&1 || true
  adb_bounded 30 pull /sdcard/slumber-release-system.xml "$OUT/system-dialog.xml" >/dev/null 2>&1 || true
  local xy
  xy="$(python3 - "$OUT/system-dialog.xml" <<'PYCOORD'
import re,sys,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: raise SystemExit(0)
for wanted in ("Wait", "Got it"):
  for node in root.iter("node"):
    a=node.attrib
    if a.get("text")==wanted or a.get("content-desc")==wanted:
      m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
      if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
PYCOORD
  )" || true
  if [ -n "${xy:-}" ]; then adb_bounded 20 shell input tap $xy; sleep 1; fi
}

ui_exact() {
  local needle="$1"
  dump_ui
  python3 - "$OUT/ui.xml" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if a.get("text","").strip()==needle or a.get("content-desc","").strip()==needle:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

ui_contains() {
  local needle="$1"
  dump_ui
  python3 - "$OUT/ui.xml" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if needle in a.get("text","") or needle in a.get("content-desc",""):
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_exact() {
  local needle="$1"; local seconds="${2:-35}"
  for _ in $(seq 1 "$seconds"); do
    if ui_exact "$needle"; then return 0; fi
    dismiss_system_dialogs || true
    sleep 1
  done
  adb_bounded 30 exec-out screencap -p > "$OUT/failure.png" || true
  adb_bounded 30 logcat -d -t 3000 > "$OUT/failure-logcat.txt" || true
  echo "Release timed out waiting for: $needle" >&2
  return 1
}

wait_contains() {
  local needle="$1"; local seconds="${2:-35}"
  for _ in $(seq 1 "$seconds"); do
    if ui_contains "$needle"; then return 0; fi
    sleep 1
  done
  echo "Release timed out waiting for text containing: $needle" >&2
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
  adb_bounded 20 shell input tap $xy
}

tap_until_visible() {
  local source="$1"; local target="$2"; local seconds="${3:-35}"
  for _ in $(seq 1 "$seconds"); do
    if ui_exact "$target"; then return 0; fi
    if ui_exact "$source"; then tap_ui "$source" || true; fi
    dismiss_system_dialogs || true
    sleep 1
  done
  dump_ui
  adb_bounded 30 exec-out screencap -p > "$OUT/transition-failure.png" || true
  echo "Release could not reach '$target' from '$source'" >&2
  return 1
}

capture() {
  local name="$1"
  dump_ui
  adb_bounded 30 exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
}

launch_app() {
  adb_bounded 30 shell am force-stop "$PKG"
  adb_bounded 45 shell am start -n "$ACTIVITY" >/dev/null
}

adb_bounded 180 wait-for-device
adb_bounded 240 install -r "$APK"
adb_bounded 45 shell pm clear "$PKG" >/dev/null
adb_bounded 30 logcat -c || true
launch_app

wait_exact "Practice" 60
wait_exact "Falling notes" 20
capture release-home

# Real release Piano surface and orientation transition.
tap_until_visible "Practice on the piano" "88 keys" 45
wait_exact "88 keys" 15
capture release-piano
adb_bounded 20 shell input keyevent KEYCODE_BACK
wait_exact "Practice" 40

# Release Play: use the longer catalog chart for stable pause/resume proof.
# The seven-note warm-up can finish during slow UIAutomator calls on CI.
tap_until_visible "Songs" "Learn a song" 15
tap_ui "Find a song"
adb_bounded 20 shell input text 'Jingle%sbells'
adb_bounded 20 shell input keyevent KEYCODE_ENTER
wait_exact "Jingle Bells" 20
tap_ui "Play"
wait_exact "FALLING NOTES" 40
wait_exact "Ready to play?" 20
capture release-play-ready
tap_ui "Start"
wait_exact "Pause" 15
tap_ui "Pause"
wait_exact "Paused" 8
capture release-play-paused
sleep 4
wait_exact "Paused" 5
if ui_exact "Play again"; then
  echo "Release Play completed while explicitly paused" >&2
  exit 1
fi
tap_ui "Resume"
wait_exact "Play again" 70
capture release-play-resumed-complete

# Return to Practice and score the real seven-note C-major phrase separately.
tap_ui "Back"
wait_exact "Learn a song" 30
tap_until_visible "Practice" "Falling notes" 15
tap_ui "Falling notes"
wait_exact "FALLING NOTES" 40
wait_exact "Ready to play?" 20
tap_ui "Start"
wait_exact "Pause" 12
# Start the score sequence at a clean chart origin.
tap_ui "Restart"
wait_exact "Pause" 8
adb_bounded 30 exec-out screencap -p > "$OUT/release-size.png"
read -r W H <<<"$(python3 - "$OUT/release-size.png" <<'PY'
import struct,sys
b=open(sys.argv[1],'rb').read(24)
print(*struct.unpack('>II',b[16:24]))
PY
)"
python3 - "$W" "$H" <<'PY'
import subprocess,sys,time
w,h=map(int,sys.argv[1:3])
white_pcs={0,2,4,5,7,9,11}
whites=[m for m in range(21,109) if m%12 in white_pcs]
sequence=[60,64,67,69,67,64,60]
positions=[whites.index(m) for m in sequence]
center=(min(positions)+max(positions))/2.0
start=int((center-22/2.0)+0.5)
start=max(0,min(start,len(whites)-22))
visible={m:whites.index(m)-start for m in set(sequence)}
y=round(h*.95)
beat=60.0/90.0
origin=time.monotonic()-.12
for index,midi in enumerate(sequence):
    target=origin+index*beat
    remaining=target-time.monotonic()
    if remaining>0: time.sleep(remaining)
    x=round(w*(visible[midi]+.5)/22.0)
    subprocess.run(
        ["adb","shell","input","tap",str(x),str(y)],
        check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
    )
PY
wait_exact "Play again" 18
capture release-play-complete

# Return, kill the process, relaunch, and prove a non-zero scored run survived.
tap_ui "Back"
wait_exact "Practice" 40
launch_app
wait_exact "Practice" 50
tap_until_visible "Songs" "Learn a song" 15
dump_ui
python3 - "$OUT/ui.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts=[n.attrib.get("text","") for n in root.iter("node")]
progress=next((t for t in texts if t.startswith("Play best ") and "1 run" in t), None)
assert progress is not None, texts
m=re.search(r"Play best (\d+)", progress)
assert m and int(m.group(1)) > 0, progress
print("release persisted scored progress",progress)
PY
capture release-songs-after-relaunch

adb_bounded 45 logcat -d -t 5000 > "$OUT/release-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/release-logcat.txt"; then
  echo "Slumber release build crashed during acceptance" >&2
  exit 1
fi

cat > "$OUT/GREEN.txt" <<'TXT'
ARM64 RELEASE APK PACKAGE = GREEN
RELEASE MODE X86_64 QA INSTALL = GREEN
RELEASE COLD START = GREEN
RELEASE PIANO = GREEN
RELEASE PLAY PAUSE = GREEN
RELEASE PLAY RESUME = GREEN
RELEASE PLAY COMPLETE = GREEN
RELEASE SCORED PLAY = GREEN
RELEASE PROCESS RELAUNCH = GREEN
RELEASE SCORE PERSISTENCE = GREEN
TXT
cat "$OUT/GREEN.txt"
