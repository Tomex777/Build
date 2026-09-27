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
    if a.get("text") == "Piano" or a.get("content-desc") == "Piano":
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit("Piano destination not found in app UI")
PY
)
  adb shell input tap "$x" "$y"
  sleep 5
  adb exec-out screencap -p > "$OUT/$label-piano.png"
  test -s "$OUT/$label-piano.png"
  adb shell uiautomator dump /sdcard/slumber.xml >/dev/null
  adb pull /sdcard/slumber.xml "$OUT/$label-piano.xml" >/dev/null
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
