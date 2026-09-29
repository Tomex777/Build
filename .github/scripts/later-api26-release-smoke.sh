#!/usr/bin/env bash
set -euo pipefail

cd later
mkdir -p qa-evidence/api26
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
  adb exec-out screencap -p > "qa-evidence/api26/$1.png"
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

adb logcat -b crash -d > qa-evidence/api26/crash.txt
if grep -q 'com.night.later' qa-evidence/api26/crash.txt; then
  cat qa-evidence/api26/crash.txt
  exit 1
fi

echo LATER_API26_RELEASE_SMOKE_PASS
