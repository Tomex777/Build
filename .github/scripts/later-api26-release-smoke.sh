#!/usr/bin/env bash
set -euo pipefail

cd later
mkdir -p qa-evidence/api26

# Preserve emulator evidence when a release-only runtime failure stops this smoke.
report_api26_failure() {
  local result="$?"
  trap - EXIT
  if [ "$result" -ne 0 ]; then
    adb logcat -b crash -d -v threadtime > qa-evidence/api26/crash-on-failure.txt 2>&1 || true
    adb logcat -d -v threadtime > qa-evidence/api26/logcat-on-failure.txt 2>&1 || true
    adb shell dumpsys activity activities > qa-evidence/api26/activities-on-failure.txt 2>&1 || true
    adb shell dumpsys package com.night.later > qa-evidence/api26/package-on-failure.txt 2>&1 || true
    adb shell uiautomator dump /sdcard/later-api26-failure.xml >/dev/null 2>&1 || true
    adb pull /sdcard/later-api26-failure.xml qa-evidence/api26/window-on-failure.xml >/dev/null 2>&1 || true
    adb exec-out screencap -p > qa-evidence/api26/screen-on-failure.png 2>/dev/null || true
  fi
  exit "$result"
}
trap report_api26_failure EXIT
APK="${LATER_QA_APK:?LATER_QA_APK must point to the QA-signed release APK}"
test -s "$APK"

adb wait-for-device
adb uninstall com.night.later >/dev/null 2>&1 || true
adb install -r "$APK"
# This smoke job creates a fresh API 26 emulator. Clearing logcat is only a
# diagnostic convenience; older API 26 logd instances can reject the clear
# immediately after a streamed APK install. Keep release validation running.
if ! adb logcat -c; then
  echo "WARN: adb logcat -c failed; continuing API 26 release sanity checks" >&2
fi

dump() {
  local name="$1" ok=0
  for attempt in 1 2 3 4 5; do
    adb shell rm -f /sdcard/later-api26.xml >/dev/null 2>&1 || true
    if adb shell uiautomator dump /sdcard/later-api26.xml >/dev/null 2>&1 &&
       adb shell test -s /sdcard/later-api26.xml; then
      ok=1
      break
    fi
    sleep 1
  done
  [ "$ok" -eq 1 ] || { echo "API 26 UI dump failed: $name" >&2; exit 1; }
  adb pull /sdcard/later-api26.xml "qa-evidence/api26/${name}.xml" >/dev/null
}

shot() {
  local out="qa-evidence/api26/$1.png"
  local raw="qa-evidence/api26/$1.raw"
  rm -f "$out" "$raw"

  for attempt in 1 2 3 4; do
    # Fast path: normal PNG stream.
    if timeout 10s adb exec-out screencap -p > "$out" 2>/dev/null && [ -s "$out" ]; then
      return 0
    fi
    rm -f "$out"

    # Some old SurfaceFlinger builds are more reliable when screencap writes on
    # device first and adb only transfers the finished file.
    adb shell rm -f /sdcard/later-api26-shot.png >/dev/null 2>&1 || true
    if timeout 10s adb shell screencap -p /sdcard/later-api26-shot.png >/dev/null 2>&1 &&
       timeout 10s adb pull /sdcard/later-api26-shot.png "$out" >/dev/null 2>&1 &&
       [ -s "$out" ]; then
      adb shell rm -f /sdcard/later-api26-shot.png >/dev/null 2>&1 || true
      return 0
    fi
    adb shell rm -f /sdcard/later-api26-shot.png >/dev/null 2>&1 || true
    rm -f "$out"

    # Android 8 can occasionally stall its PNG encoder even while the composed
    # framebuffer and UI hierarchy are healthy. Capture the real raw RGBA
    # framebuffer and encode it on the host instead of accepting a fake/blank
    # screenshot or weakening the visual-evidence gate.
    if timeout 10s adb exec-out screencap > "$raw" 2>/dev/null && [ -s "$raw" ]; then
      if python3 - "$raw" "$out" <<'PY'
import binascii
import struct
import sys
import zlib

raw_path, png_path = sys.argv[1], sys.argv[2]
blob = open(raw_path, "rb").read()
if len(blob) < 12:
    raise SystemExit("raw screencap header is incomplete")

width, height, pixel_format = struct.unpack_from("<III", blob, 0)
if not (0 < width <= 4096 and 0 < height <= 4096):
    raise SystemExit(f"invalid raw screencap dimensions: {width}x{height}")
if pixel_format not in (1, 2, 3, 4, 5):
    raise SystemExit(f"unsupported raw screencap pixel format: {pixel_format}")

rgba_bytes = width * height * 4
payload = blob[12:12 + rgba_bytes]
if len(payload) != rgba_bytes:
    raise SystemExit(
        f"raw screencap pixel payload is incomplete: got {len(payload)}, expected {rgba_bytes}"
    )

def chunk(kind: bytes, data: bytes) -> bytes:
    checksum = binascii.crc32(kind + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", checksum)

scanlines = b"".join(
    b"\x00" + payload[y * width * 4:(y + 1) * width * 4]
    for y in range(height)
)
png = (
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(scanlines, 6))
    + chunk(b"IEND", b"")
)
open(png_path, "wb").write(png)
print(f"encoded raw API 26 framebuffer {width}x{height} format={pixel_format}")
PY
      then
        rm -f "$raw"
        [ -s "$out" ] && return 0
      fi
    fi
    rm -f "$out" "$raw"
    echo "Retrying API 26 screenshot capture ($attempt/4)" >&2
    sleep 2
  done

  echo "API 26 screenshot capture produced no pixels after all framebuffer paths: $out" >&2
  return 1
}

click_desc() {
  python3 qa_click.py "$1" desc-exact "$2"
}

click_text() {
  python3 qa_click.py "$1" text-exact "$2"
}

click_contains() {
  python3 qa_click.py "$1" text-contains "$2"
}

assert_label() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); q=sys.argv[2]
if not any(q in n.attrib.get('text','') or q in n.attrib.get('content-desc','') for n in root.iter('node')):
    raise SystemExit(f'missing {q!r} in {sys.argv[1]}')
PY
}

adb shell dumpsys package com.night.later > qa-evidence/api26/package.txt
grep -q 'versionName=2.2.4' qa-evidence/api26/package.txt
grep -q 'versionCode=20260928' qa-evidence/api26/package.txt
grep -q 'minSdk=26' qa-evidence/api26/package.txt
grep -q 'targetSdk=36' qa-evidence/api26/package.txt

adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 4
test -n "$(adb shell pidof -s com.night.later | tr -d '\r')"

dump home
shot home
assert_label qa-evidence/api26/home.xml 'Later'
click_desc qa-evidence/api26/home.xml 'Create capsule'
sleep 3

dump editor
shot editor
assert_label qa-evidence/api26/editor.xml 'Title'
assert_label qa-evidence/api26/editor.xml 'Write to your future self'

click_text qa-evidence/api26/editor.xml 'Title'
sleep 0.5
adb shell input text 'API26Release'
sleep 0.7
adb shell input keyevent 4
sleep 0.5

dump editor-body
click_contains qa-evidence/api26/editor-body.xml 'Write to your future self'
sleep 0.5
adb shell input text 'LaterAPI26'
sleep 1
adb shell input keyevent 4

for attempt in 1 2 3 4 5 6; do
  sleep 1
  dump autosave
  if grep -q 'text="Draft autosaved"' qa-evidence/api26/autosave.xml; then break; fi
done
assert_label qa-evidence/api26/autosave.xml 'Draft autosaved'
assert_label qa-evidence/api26/autosave.xml 'API26Release'
assert_label qa-evidence/api26/autosave.xml 'LaterAPI26'
shot autosave

adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 3
dump relaunch
shot relaunch
assert_label qa-evidence/api26/relaunch.xml 'LaterAPI26'

# Hand the persisted release draft to the media acceptance script in its expected
# editor state; that script covers picker, viewers, image edit and video export.
click_text qa-evidence/api26/relaunch.xml 'Edit'
sleep 3
dump editor-after-relaunch
shot editor-after-relaunch
assert_label qa-evidence/api26/editor-after-relaunch.xml 'Media'

adb logcat -b crash -d > qa-evidence/api26/crash.txt
if grep -q 'com.night.later' qa-evidence/api26/crash.txt; then
  cat qa-evidence/api26/crash.txt
  exit 1
fi

echo LATER_API26_RELEASE_SMOKE_PASS
