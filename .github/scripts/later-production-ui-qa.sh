#!/usr/bin/env bash
set -euo pipefail

PKG="com.night.later"
OUT="later/qa-evidence/production-ui"
mkdir -p "$OUT"

dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/later-production.xml >/dev/null
  adb pull /sdcard/later-production.xml "$OUT/$name.xml" >/dev/null
}

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
}

click_match() {
  local file="$1"
  local needle="$2"
  local mode="${3:-either}"
  python3 - "$file" "$needle" "$mode" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, needle, mode = sys.argv[1:4]
root = ET.parse(path).getroot()
candidates = []
for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    match = (
        (mode in ("text", "either") and text == needle) or
        (mode in ("desc", "either") and desc == needle)
    )
    if not match:
        continue
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        continue
    x1,y1,x2,y2 = map(int,m.groups())
    candidates.append(((x1+x2)//2,(y1+y2)//2))
if not candidates:
    raise SystemExit(2)
print(*candidates[0])
PY
}

tap_label() {
  local needle="$1"
  local mode="${2:-either}"
  local tries="${3:-1}"
  local i coords
  for ((i=1; i<=tries; i++)); do
    dump_ui "probe"
    if coords="$(click_match "$OUT/probe.xml" "$needle" "$mode" 2>/dev/null)"; then
      adb shell input tap $coords
      sleep 1
      return 0
    fi
    adb shell input swipe 160 520 160 180 350
    sleep 0.5
  done
  echo "Unable to find production UI label: $needle" >&2
  return 1
}

assert_has() {
  local file="$1"
  local text="$2"
  grep -Fq "$text" "$file" || {
    echo "Missing production UI copy: $text" >&2
    exit 1
  }
}

assert_clean() {
  local file="$1"
  local forbidden
  for forbidden in "offline app" "block manifest" "AES-256-GCM" "Android Keystore" "app cache" "ciphertext"; do
    if grep -Fiq "$forbidden" "$file"; then
      echo "Implementation-oriented UI copy leaked: $forbidden" >&2
      exit 1
    fi
  done
}

adb shell pm clear "$PKG" >/dev/null || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 2

dump_ui home
shot home
tap_label "Settings" desc 2
dump_ui settings-main
shot settings-main

tap_label "Security" text 8
dump_ui security
shot security
assert_has "$OUT/security.xml" "How Later protects your capsules."
assert_has "$OUT/security.xml" "Encrypted on device"
assert_clean "$OUT/security.xml"

adb shell input keyevent 4
sleep 1
tap_label "Storage & data" text 8
dump_ui storage
shot storage
assert_has "$OUT/storage.xml" "Manage storage and export an encrypted archive."
assert_clean "$OUT/storage.xml"

adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
sleep 1
dump_ui home-return
tap_label "Open navigation" desc 2
dump_ui drawer
shot drawer
assert_has "$OUT/drawer.xml" "Appearance, privacy, and storage."
if grep -Fq "Settings stays on Home." "$OUT/drawer.xml"; then
  echo "Redundant Home settings copy remains" >&2
  exit 1
fi

echo "LATER_PRODUCTION_UI_QA_PASS"
