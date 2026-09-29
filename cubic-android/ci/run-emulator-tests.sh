#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${CUBIC_APK:-$ROOT/cubic-android/app/build/outputs/apk/debug/app-debug.apk}"
EXTENDED="${CUBIC_EXTENDED:-0}"
OUT="$ROOT/cubic-android/build/emulator-screenshots"
REPORT="$ROOT/cubic-android/build/ci-report"

rm -rf "$OUT" "$REPORT"
mkdir -p "$OUT" "$REPORT"

refresh_ui() {
  adb shell uiautomator dump /sdcard/cubic-window.xml >/dev/null
  adb pull /sdcard/cubic-window.xml "$REPORT/window.xml" >/dev/null
}

assert_cached() {
  local wanted="$1"
  if ! grep -Fq "$wanted" "$REPORT/window.xml"; then
    echo "Expected UI text not found: $wanted" >&2
    cat "$REPORT/window.xml" >&2
    exit 1
  fi
}

coords_from_cache() {
  local wanted="$1"
  python3 - "$REPORT/window.xml" "$wanted" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()

for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    if text == wanted or desc == wanted or wanted in text or wanted in desc:
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            left, top, right, bottom = map(int, match.groups())
            print((left + right) // 2, (top + bottom) // 2)
            raise SystemExit(0)

raise SystemExit(2)
PY
}

tap_cached() {
  local wanted="$1"
  local coords
  coords="$(coords_from_cache "$wanted")" || {
    echo "Could not locate cached control: $wanted" >&2
    cat "$REPORT/window.xml" >&2
    exit 1
  }
  read -r x y <<<"$coords"
  adb shell input tap "$x" "$y"
  sleep 0.2
}

tap_repeat_cached() {
  local wanted="$1"
  local count="$2"
  for _ in $(seq 1 "$count"); do
    tap_cached "$wanted"
  done
}

launch_app() {
  adb shell am force-stop com.tomex777.cubic
  adb shell am start -W -n com.tomex777.cubic/.MainActivity | tee "$REPORT/launch.txt"
  sleep 2
}

viewport_metrics() {
  local png="$1"
  python3 - "$png" <<'PY'
import hashlib
import struct
import sys
import zlib

path = sys.argv[1]
data = open(path, "rb").read()

if data[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("not a PNG: " + path)

pos = 8
idat = []
width = height = bit_depth = color_type = interlace = None

while pos < len(data):
    length = struct.unpack(">I", data[pos:pos + 4])[0]
    kind = data[pos + 4:pos + 8]
    payload = data[pos + 8:pos + 8 + length]
    pos += 12 + length
    if kind == b"IHDR":
        width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
    elif kind == b"IDAT":
        idat.append(payload)
    elif kind == b"IEND":
        break

if bit_depth != 8 or color_type not in (2, 6) or interlace != 0:
    raise SystemExit(
        f"unsupported PNG depth={bit_depth} color={color_type} interlace={interlace}"
    )

channels = 3 if color_type == 2 else 4
stride = width * channels
encoded = zlib.decompress(b"".join(idat))
rows = []
offset = 0
previous = bytearray(stride)

def paeth(a, b, c):
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c

for _ in range(height):
    filter_type = encoded[offset]
    offset += 1
    row = bytearray(encoded[offset:offset + stride])
    offset += stride

    for i in range(stride):
        left = row[i - channels] if i >= channels else 0
        up = previous[i]
        upper_left = previous[i - channels] if i >= channels else 0

        if filter_type == 1:
            row[i] = (row[i] + left) & 0xFF
        elif filter_type == 2:
            row[i] = (row[i] + up) & 0xFF
        elif filter_type == 3:
            row[i] = (row[i] + ((left + up) // 2)) & 0xFF
        elif filter_type == 4:
            row[i] = (row[i] + paeth(left, up, upper_left)) & 0xFF
        elif filter_type != 0:
            raise SystemExit(f"unsupported PNG filter {filter_type}")

    rows.append(row)
    previous = row

# Crop only the 3D viewport: exclude status/header and the bottom Controls pill.
x0 = int(width * 0.05)
x1 = int(width * 0.95)
y0 = int(height * 0.16)
y1 = int(height * 0.78)

visible = 0
total = 0
crop_bytes = bytearray()

for y in range(y0, y1):
    row = rows[y]
    for x in range(x0, x1):
        i = x * channels
        r, g, b = row[i], row[i + 1], row[i + 2]
        crop_bytes.extend((r, g, b))
        high = max(r, g, b)
        low = min(r, g, b)

        # Count colored stickers and bright white faces, while rejecting
        # the near-black open-space background.
        if high >= 55 and ((high - low) >= 25 or low >= 135):
            visible += 1
        total += 1

ratio = visible / max(total, 1)
digest = hashlib.sha256(crop_bytes).hexdigest()

if ratio < 0.025:
    raise SystemExit(
        f"3D viewport appears blank in {path}: visible_ratio={ratio:.4f}"
    )

print(f"{ratio:.6f} {digest}")
PY
}

capture_viewport() {
  local name="$1"
  local path="$OUT/$name.png"
  adb exec-out screencap -p > "$path"

  local metrics
  metrics="$(viewport_metrics "$path")"
  printf '%s %s\n' "$name" "$metrics" | tee -a "$REPORT/viewport-metrics.txt"
}

open_controls() {
  refresh_ui
  tap_cached "Open controls"
  sleep 0.4
  refresh_ui
}

close_controls() {
  tap_cached "Close controls"
  sleep 0.35
  refresh_ui
  assert_cached "Controls"
}

capture_model_state() {
  local name="$1"
  local dimension="$2"

  close_controls
  assert_cached "$dimension"
  capture_viewport "$name"

  open_controls
  assert_cached "$dimension"
}

adb wait-for-device
adb install -r "$APK"
launch_app

PID="$(adb shell pidof com.tomex777.cubic | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "Cubic process is not running" >&2
  exit 1
fi
printf '%s\n' "$PID" > "$REPORT/pid.txt"

if ! adb shell dumpsys activity activities | grep -q "com.tomex777.cubic/.MainActivity"; then
  echo "Cubic MainActivity is not resumed" >&2
  exit 1
fi

adb shell getprop ro.build.version.sdk | tr -d '\r' > "$REPORT/device-api.txt"
adb shell dumpsys package com.tomex777.cubic > "$REPORT/package.txt"
grep -q "versionName=1.0.0" "$REPORT/package.txt"

refresh_ui
assert_cached "Cubic"
assert_cached "3 × 3 × 3"
assert_cached "Solved"
assert_cached "Controls"

capture_viewport "cubic-home"
read -r home_ratio home_hash < <(viewport_metrics "$OUT/cubic-home.png")

# Prove that orbit changes the actual 3D viewport rather than merely
# changing unrelated status-bar pixels.
adb shell input touchscreen swipe 260 650 800 560 450
sleep 0.7
capture_viewport "cubic-orbit"
read -r orbit_ratio orbit_hash < <(viewport_metrics "$OUT/cubic-orbit.png")

if [[ "$home_hash" == "$orbit_hash" ]]; then
  echo "Orbit gesture did not change the central 3D viewport" >&2
  exit 1
fi

open_controls
assert_cached "Counterclockwise"
adb exec-out screencap -p > "$OUT/cubic-controls-sheet.png"

if [[ "$EXTENDED" == "1" ]]; then
  # Every outer face and direction must be reversible at runtime.
  for face in R L U D F B; do
    tap_cached "Face $face"
    tap_cached "Turn clockwise"
    tap_cached "Turn counterclockwise"
  done
  refresh_ui
  assert_cached "Solved"

  # 2x2x2 visual + runtime proof.
  tap_cached "Decrease Width"
  tap_cached "Decrease Height"
  tap_cached "Decrease Depth"
  refresh_ui
  assert_cached "2 × 2 × 2"
  assert_cached "Solved"
  capture_model_state "cubic-2x2x2" "2 × 2 × 2"

  # 4x4x4 and an inner-layer round trip.
  tap_cached "Increase Width"
  tap_cached "Increase Height"
  tap_cached "Increase Depth"
  tap_cached "Increase Width"
  tap_cached "Increase Height"
  tap_cached "Increase Depth"
  refresh_ui
  assert_cached "4 × 4 × 4"

  tap_cached "Face R"
  tap_cached "Next layer"
  tap_cached "Turn clockwise"
  tap_cached "Turn counterclockwise"
  refresh_ui
  assert_cached "Layer 2 of 4"
  assert_cached "Solved"
  capture_model_state "cubic-4x4x4" "4 × 4 × 4"

  # 3x3x5 true cuboid. R has a rectangular 3x5 cross-section, so a
  # requested quarter-turn is normalized to a legal 180-degree turn.
  tap_cached "Decrease Width"
  tap_cached "Decrease Height"
  tap_cached "Increase Depth"
  refresh_ui
  assert_cached "3 × 3 × 5"
  capture_model_state "cubic-3x3x5" "3 × 3 × 5"

  tap_cached "Face R"
  tap_cached "Turn clockwise"
  tap_cached "Turn clockwise"
  refresh_ui
  assert_cached "Solved"

  # 2x4x6: scramble, render, undo and reset.
  tap_cached "Decrease Width"
  tap_cached "Increase Height"
  tap_cached "Increase Depth"
  refresh_ui
  assert_cached "2 × 4 × 6"

  tap_cached "Scramble"
  refresh_ui
  assert_cached "18 moves"
  capture_model_state "cubic-2x4x6-scrambled" "2 × 4 × 6"

  tap_cached "Undo"
  refresh_ui
  assert_cached "17 moves"

  tap_cached "Reset"
  refresh_ui
  assert_cached "Solved"

  # Larger practical render sample.
  tap_repeat_cached "Increase Width" 4
  tap_repeat_cached "Increase Height" 2
  refresh_ui
  assert_cached "6 × 6 × 6"
  assert_cached "Solved"
  capture_model_state "cubic-6x6x6" "6 × 6 × 6"
fi

# Cold-start for the guided 3x3 proof.
launch_app
refresh_ui
assert_cached "3 × 3 × 3"
assert_cached "Solved"
assert_cached "Controls"

open_controls
tap_cached "Scramble"
refresh_ui
assert_cached "18 moves"
capture_model_state "cubic-scrambled" "3 × 3 × 3"

tap_cached "Learn"
sleep 0.4
refresh_ui
assert_cached "Next move:"
adb exec-out screencap -p > "$OUT/cubic-guided-step.png"

# The scramble contains exactly 18 recorded legal moves.
tap_repeat_cached "Do this move" 18

refresh_ui
assert_cached "3 × 3 × 3"
assert_cached "Solved"
adb exec-out screencap -p > "$OUT/cubic-guided-solved.png"

tap_cached "Play"
close_controls
capture_viewport "cubic-final-solved"

adb logcat -d -t 700 > "$REPORT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|AndroidRuntime: FATAL|Cubic shader (compile|link) failed" "$REPORT/logcat.txt"; then
  echo "Fatal renderer/app error found in Cubic logcat" >&2
  exit 1
fi

echo "CUBIC RELEASE RUNTIME = GREEN" | tee "$REPORT/result.txt"
