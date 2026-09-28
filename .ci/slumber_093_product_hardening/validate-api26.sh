#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/slumber-088/app/build/outputs/apk/debug/app-debug.apk}"
OUT=/tmp/slumber-api26-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"
adb logcat -c || true

dump_ui() {
  adb shell uiautomator dump /sdcard/slumber-api26.xml >/dev/null 2>&1 || true
  adb pull /sdcard/slumber-api26.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
}
ui_has() {
  local needle="$1"
  dump_ui
  python3 - "$OUT/ui.xml" "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET
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
  local needle="$1"; local seconds="${2:-30}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$needle"; then return 0; fi
    sleep 1
  done
  adb exec-out screencap -p > "$OUT/failure.png" || true
  adb logcat -d -t 2500 > "$OUT/failure-logcat.txt" || true
  echo "API 26 timed out waiting for: $needle" >&2
  return 1
}
tap_ui() {
  local needle="$1"
  dump_ui
  local xy
  xy="$(python3 - "$OUT/ui.xml" "$needle" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if a.get("text","").strip()==needle or a.get("content-desc","").strip()==needle:
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit(1)
PY
)"
  adb shell input tap $xy
}

adb install -r "$APK" >/dev/null
adb shell pm clear "$PKG" >/dev/null || true
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 35
wait_for "Falling notes" 10

tap_ui "Songs"
wait_for "Learn a song" 25
wait_for "C major warm-up" 10
tap_ui "Practice"
wait_for "Practice" 20

tap_ui "Start practice"
wait_for "88 keys" 35
adb exec-out screencap -p > "$OUT/piano-api26.png"
test -s "$OUT/piano-api26.png"
adb shell input keyevent KEYCODE_BACK
wait_for "Practice" 30

tap_ui "Settings"
wait_for "Make Slumber yours" 25
wait_for "Sounds" 10
adb exec-out screencap -p > "$OUT/settings-api26.png"
test -s "$OUT/settings-api26.png"

adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 30
adb shell pidof "$PKG" >/dev/null

adb logcat -d -t 5000 > "$OUT/api26-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/api26-logcat.txt"; then
  echo "Slumber crashed on API 26" >&2
  exit 1
fi

cat > "$OUT/GREEN.txt" <<'TXT'
API 26 INSTALL = GREEN
API 26 PRACTICE = GREEN
API 26 PIANO = GREEN
API 26 SONGS = GREEN
API 26 SETTINGS = GREEN
API 26 PROCESS RELAUNCH = GREEN
TXT
cat "$OUT/GREEN.txt"
