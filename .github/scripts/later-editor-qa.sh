#!/usr/bin/env bash
set -euo pipefail

cd later
mkdir -p qa-evidence
gradle installDebug --stacktrace --console=plain

cat > qa_click.py <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
xml_path, mode, needle = sys.argv[1:4]
root = ET.parse(xml_path).getroot()
nodes = list(root.iter('node'))

def bounds(n):
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds', ''))
    return tuple(map(int, m.groups())) if m else None

def value(n):
    return ' '.join((n.attrib.get('text',''), n.attrib.get('content-desc',''))).strip()

found = None
if mode == 'text-exact':
    found = next((n for n in nodes if n.attrib.get('text') == needle), None)
elif mode == 'text-contains':
    found = next((n for n in nodes if needle in n.attrib.get('text','')), None)
elif mode == 'desc-exact':
    found = next((n for n in nodes if n.attrib.get('content-desc') == needle), None)
elif mode == 'label-exact':
    found = next((n for n in nodes if n.attrib.get('text') == needle or n.attrib.get('content-desc') == needle), None)
elif mode == 'right-clickable-of-text':
    a = next((n for n in nodes if n.attrib.get('text') == needle), None)
    if a and bounds(a):
        _, ay1, ax2, ay2 = bounds(a)
        candidates = []
        for n in nodes:
            b = bounds(n)
            if n.attrib.get('clickable') == 'true' and b:
                x1, y1, x2, y2 = b
                if x1 >= ax2 and not (y2 < ay1 - 140 or y1 > ay2 + 140):
                    candidates.append(((x2-x1)*(y2-y1), x1, n))
        if candidates:
            found = sorted(candidates, key=lambda t: (t[0], t[1]))[0][2]
elif mode == 'first-clickable-below-text':
    a = next((n for n in nodes if n.attrib.get('text') == needle), None)
    if a and bounds(a):
        ay2 = bounds(a)[3]
        candidates = []
        for n in nodes:
            b = bounds(n)
            if n.attrib.get('clickable') == 'true' and b and b[1] >= ay2:
                candidates.append((b[1], b[0], (b[2]-b[0])*(b[3]-b[1]), n))
        if candidates:
            found = sorted(candidates, key=lambda t: (t[0], t[1], t[2]))[0][3]

if not found or not bounds(found):
    print(open(xml_path, encoding='utf-8').read())
    raise SystemExit(f'node not found: {mode} {needle}')

x1, y1, x2, y2 = bounds(found)
x, y = (x1+x2)//2, (y1+y2)//2
print(f'click {mode} {needle!r} at {x},{y} bounds={found.attrib.get("bounds")} label={value(found)!r}')
subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)], check=True)
PY

dump() {
  local name="$1"
  adb shell uiautomator dump /sdcard/later-window.xml >/dev/null
  adb pull /sdcard/later-window.xml "qa-evidence/${name}.xml" >/dev/null
}
shot() { adb exec-out screencap -p > "qa-evidence/$1.png"; }
click_text() { python3 qa_click.py "$1" text-exact "$2"; }
click_contains() { python3 qa_click.py "$1" text-contains "$2"; }
click_desc() { python3 qa_click.py "$1" desc-exact "$2"; }
click_label() { python3 qa_click.py "$1" label-exact "$2"; }

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

assert_desc() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot(); query = sys.argv[2]
if query not in [n.attrib.get('content-desc','') for n in root.iter('node')]:
    raise SystemExit(f'missing desc {query!r}')
PY
}

assert_editor() {
  local f="$1"
  for l in Seal Media Voice Attachment Sticker Format; do assert_label "$f" "$l"; done
  assert_desc "$f" 'Go back'
}

adb wait-for-device
adb logcat -c
adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity
sleep 4
test -n "$(adb shell pidof -s com.night.later | tr -d '\r')"

dump home; shot home
click_desc qa-evidence/home.xml 'Create capsule'
sleep 3
dump editor-initial; shot editor-initial
assert_editor qa-evidence/editor-initial.xml
assert_label qa-evidence/editor-initial.xml 'Title'
assert_label qa-evidence/editor-initial.xml 'Write to your future self'

click_text qa-evidence/editor-initial.xml 'Title'
adb shell input text 'LaterEditorQA'
sleep 1
dump editor-title-keyboard; shot editor-title-keyboard
assert_label qa-evidence/editor-title-keyboard.xml 'LaterEditorQA'
adb shell input keyevent 4; sleep 1

dump editor-pre-body
click_contains qa-evidence/editor-pre-body.xml 'Write to your future self'
adb shell input text 'EditorBodyQA'
sleep 1
dump editor-body-keyboard; shot editor-body-keyboard
assert_label qa-evidence/editor-body-keyboard.xml 'EditorBodyQA'
adb shell input keyevent 4; sleep 1
dump editor-body; shot editor-body
assert_editor qa-evidence/editor-body.xml

click_label qa-evidence/editor-body.xml 'Format'
sleep 1
dump format-home; shot format-home
for l in Home Paragraph Paper; do assert_label qa-evidence/format-home.xml "$l"; done
assert_desc qa-evidence/format-home.xml 'Undo'; assert_desc qa-evidence/format-home.xml 'Redo'
click_label qa-evidence/format-home.xml 'Paragraph'; sleep 1
dump format-paragraph; shot format-paragraph
assert_label qa-evidence/format-paragraph.xml 'Bullets'; assert_label qa-evidence/format-paragraph.xml 'Numbered'
click_label qa-evidence/format-paragraph.xml 'Paper'; sleep 1
dump format-paper; shot format-paper
adb shell input keyevent 4; sleep 1

dump editor-before-sticker
click_label qa-evidence/editor-before-sticker.xml 'Sticker'; sleep 1
dump stickers; shot stickers; assert_label qa-evidence/stickers.xml 'Stickers'
python3 qa_click.py qa-evidence/stickers.xml first-clickable-below-text 'Stickers'
sleep 1; dump editor-after-sticker; shot editor-after-sticker; assert_editor qa-evidence/editor-after-sticker.xml

python3 qa_click.py qa-evidence/editor-after-sticker.xml right-clickable-of-text 'LaterEditorQA'
sleep 1; dump mood; shot mood
assert_label qa-evidence/mood.xml 'How does this moment feel?'
assert_label qa-evidence/mood.xml 'Choose the one that fits this moment.'
adb shell input keyevent 4; sleep 1

dump editor-before-seal
click_label qa-evidence/editor-before-seal.xml 'Seal'; sleep 1
dump seal; shot seal
assert_label qa-evidence/seal.xml 'Date'; assert_label qa-evidence/seal.xml 'Time'
assert_label qa-evidence/seal.xml 'Choose a time in the future.'; assert_label qa-evidence/seal.xml 'Seal capsule'
adb shell input keyevent 4; sleep 1

for control in Media Attachment; do
  lower="$(printf '%s' "$control" | tr '[:upper:]' '[:lower:]')"
  dump "editor-before-${lower}"; click_label "qa-evidence/editor-before-${lower}.xml" "$control"; sleep 2
  dump "${lower}-picker"; shot "${lower}-picker"; adb shell input keyevent 4; sleep 1
  dump "editor-after-${lower}"; assert_editor "qa-evidence/editor-after-${lower}.xml"
done

dump editor-before-voice; click_label qa-evidence/editor-before-voice.xml 'Voice'; sleep 2
dump voice-entry; shot voice-entry; adb shell input keyevent 4 || true; sleep 1; dump editor-after-voice
if ! grep -Eq 'text="Voice"|content-desc="Voice"' qa-evidence/editor-after-voice.xml; then
  adb shell input keyevent 4 || true; sleep 1; dump editor-after-voice
fi
assert_editor qa-evidence/editor-after-voice.xml

adb shell input swipe 160 540 160 180 450; sleep 1
dump editor-scrolled; shot editor-scrolled
sleep 3; dump editor-autosave; shot editor-autosave
click_desc qa-evidence/editor-autosave.xml 'Go back'; sleep 2; dump home-after-editor; shot home-after-editor

adb logcat -d > qa-evidence/logcat.txt
adb logcat -b crash -d > qa-evidence/crash.txt
if grep -q 'com.night.later' qa-evidence/crash.txt; then
  cat qa-evidence/crash.txt
  exit 1
fi

echo 'LATER_EDITOR_QA_PASS'
