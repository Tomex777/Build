#!/usr/bin/env bash
set -euo pipefail

cd later

# The main editor QA already installed the app and left qa_click.py in this directory.
dump() {
  local name="$1"
  local ok=0
  for attempt in 1 2 3 4 5; do
    adb shell rm -f /sdcard/later-window.xml >/dev/null 2>&1 || true
    if adb shell uiautomator dump /sdcard/later-window.xml >/dev/null 2>&1 && adb shell test -s /sdcard/later-window.xml; then
      ok=1
      break
    fi
    sleep 1
  done
  [ "$ok" -eq 1 ] || { echo "uiautomator dump failed for $name" >&2; exit 1; }
  adb pull /sdcard/later-window.xml "qa-evidence/${name}.xml" >/dev/null
}

assert_label() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot(); query = sys.argv[2]
for n in root.iter('node'):
    if query in n.attrib.get('text','') or query in n.attrib.get('content-desc',''):
        raise SystemExit(0)
raise SystemExit(f'missing label {query!r}')
PY
}

click_label() { python3 qa_click.py "$1" label-exact "$2"; }
click_contains() { python3 qa_click.py "$1" text-contains "$2"; }

adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 3

dump format-scroll-home
python3 qa_click.py qa-evidence/format-scroll-home.xml desc-exact 'Create capsule'
sleep 2
dump format-scroll-editor
click_contains qa-evidence/format-scroll-editor.xml 'Write to your future self'
sleep 1
adb shell input text 'FormatScrollQA'
sleep 1
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
sleep 0.5
dump format-scroll-selected
click_label qa-evidence/format-scroll-selected.xml 'Format'
sleep 1

dump format-scroll-top
assert_label qa-evidence/format-scroll-top.xml 'Home'
assert_label qa-evidence/format-scroll-top.xml 'Bold'
cat qa-evidence/format-scroll-top.xml > qa-evidence/format-scroll-history.txt

# On the 320x640 validation device these controls live below the first viewport.
# Scroll the Home ribbon and require the lower Home controls to become reachable.
cp qa-evidence/format-scroll-top.xml qa-evidence/format-scroll-lower.xml
for i in 1 2 3 4 5; do
  if grep -q 'text="Custom size"' qa-evidence/format-scroll-lower.xml && grep -q 'text="Apply"' qa-evidence/format-scroll-lower.xml; then
    break
  fi
  adb shell input swipe 160 560 160 290 500
  sleep 0.6
  dump format-scroll-lower
  cat qa-evidence/format-scroll-lower.xml >> qa-evidence/format-scroll-history.txt
done

assert_label qa-evidence/format-scroll-lower.xml 'Custom size'
assert_label qa-evidence/format-scroll-lower.xml 'Apply'
adb exec-out screencap -p > qa-evidence/format-scroll-lower.png

echo LATER_FORMAT_SCROLL_QA_PASS
