#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/slumber-keyboard-audit
mkdir -p "$OUT"

capture() {
  local label="$1"
  local apk="$2"
  adb install -r "$apk"
  adb shell pm clear com.night.pianohub >/dev/null || true
  adb shell am force-stop com.night.pianohub
  adb shell am start -W -n com.night.pianohub/.MainActivity
  sleep 5
  adb shell uiautomator dump /sdcard/slumber.xml >/dev/null
  adb pull /sdcard/slumber.xml "$OUT/$label-home.xml" >/dev/null
  local x y
  read -r x y < <(python3 - "$OUT/$label-home.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    a = node.attrib
    # 0.7.x exposed Piano as a bottom tab. 0.8.x uses a Start/Continue
    # practice button; its nearby “Start playing” heading is decorative.
    if a.get("text") in {"Piano", "Start", "Continue", "Start practice", "Continue practice"} or a.get("content-desc") in {"Piano", "Start", "Continue", "Start practice", "Continue practice"}:
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit("Neither Piano nor Start playing navigation was found in app UI")
PY
)
  adb shell input tap "$x" "$y"
  sleep 5
  adb shell uiautomator dump /sdcard/slumber-dialog.xml >/dev/null
  adb pull /sdcard/slumber-dialog.xml "$OUT/$label-dialog.xml" >/dev/null
  python3 - "$OUT/$label-dialog.xml" "$OUT/$label-dialog-coords.txt" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    a = node.attrib
    if a.get("text") == "Got it" or a.get("content-desc") == "Got it":
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            open(sys.argv[2], "w").write(f"{(x1+x2)//2} {(y1+y2)//2}\n")
            break
PY
  if [ -s "$OUT/$label-dialog-coords.txt" ]; then
    read -r x y < "$OUT/$label-dialog-coords.txt"
    adb shell input tap "$x" "$y"
    sleep 2
  fi
  adb exec-out screencap -p > "$OUT/$label-piano.png"
  test -s "$OUT/$label-piano.png"
  adb shell uiautomator dump /sdcard/slumber.xml >/dev/null
adb pull /sdcard/slumber.xml "$OUT/$label-piano.xml" >/dev/null
python3 - "$OUT/$label-piano.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
labels = {node.attrib.get("text", "").strip() for node in root.iter("node")}
assert "88 keys" in labels and "Got it" not in labels, "piano controls are missing or a system dialog is still covering the screen"
PY
  python3 - "$OUT/$label-piano.png" <<'PY'
import struct, sys
data = open(sys.argv[1], "rb").read(24)
assert data[:8] == b"\x89PNG\r\n\x1a\n", "capture is not PNG"
width, height = struct.unpack(">II", data[16:24])
print(f"{sys.argv[1]}: {width}x{height}")
assert width > height, "historical piano screen did not rotate to landscape"
PY
}

capture 070 /tmp/slumber-070/app/build/outputs/apk/debug/app-debug.apk
capture 086 /tmp/slumber-086/app/build/outputs/apk/debug/app-debug.apk
