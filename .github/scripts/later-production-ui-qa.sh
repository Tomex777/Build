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
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
click_match() {
  local file="$1" needle="$2" mode="${3:-either}"
  python3 - "$file" "$needle" "$mode" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path,needle,mode=sys.argv[1:4]
root=ET.parse(path).getroot()
for node in root.iter("node"):
    text=node.attrib.get("text",""); desc=node.attrib.get("content-desc","")
    if not ((mode in ("text","either") and text==needle) or (mode in ("desc","either") and desc==needle)):
        continue
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.attrib.get("bounds",""))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit(2)
PY
}
tap_label() {
  local needle="$1" mode="${2:-either}" tries="${3:-1}" i coords
  for ((i=1;i<=tries;i++)); do
    dump_ui probe
    if coords="$(click_match "$OUT/probe.xml" "$needle" "$mode" 2>/dev/null)"; then
      adb shell input tap $coords; sleep 1; return 0
    fi
    adb shell input swipe 160 520 160 180 350; sleep 0.5
  done
  echo "Unable to find production UI label: $needle" >&2; return 1
}
assert_has() { grep -Fq "$2" "$1" || { echo "Missing production UI copy: $2" >&2; exit 1; }; }
assert_clean() {
  local file="$1" forbidden
  for forbidden in "offline app" "block manifest" "AES-256-GCM" "Android Keystore" "app cache" "ciphertext" "Everything has a place now." "Export encrypted archive"; do
    if grep -Fiq "$forbidden" "$file"; then echo "Internal/redundant UI copy leaked: $forbidden" >&2; exit 1; fi
  done
}

adb shell am force-stop "$PKG" || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 2
dump_ui home; shot home
tap_label "Settings" desc 2
dump_ui settings-main; shot settings-main
assert_clean "$OUT/settings-main.xml"
if grep -Fq 'text="Security"' "$OUT/settings-main.xml"; then
  echo "Redundant Security-only settings row remains" >&2; exit 1
fi

tap_label "Storage & data" text 8
dump_ui storage; shot storage
assert_has "$OUT/storage.xml" "Manage storage and keep a recoverable backup."
assert_has "$OUT/storage.xml" "Create backup"

# Material 3 can open this sheet partially on compact screens. Exercise the
# production drag interaction before validating actions below the fold.
if ! grep -Fq "Restore backup" "$OUT/storage.xml"; then
  adb shell input swipe 160 344 160 92 450
  sleep 1
  dump_ui storage-expanded; shot storage-expanded
else
  cp "$OUT/storage.xml" "$OUT/storage-expanded.xml"
  cp "$OUT/storage.png" "$OUT/storage-expanded.png"
fi
assert_has "$OUT/storage-expanded.xml" "Restore backup"
assert_clean "$OUT/storage.xml"
assert_clean "$OUT/storage-expanded.xml"

adb shell input keyevent 4; sleep 1
adb shell input keyevent 4; sleep 1
dump_ui home-return
tap_label "Open navigation" desc 2
dump_ui drawer; shot drawer
assert_has "$OUT/drawer.xml" "Appearance, privacy, and storage."
if grep -Fq "Settings stays on Home." "$OUT/drawer.xml"; then echo "Redundant Home settings copy remains" >&2; exit 1; fi

echo "LATER_PRODUCTION_UI_QA_PASS"
