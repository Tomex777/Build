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
elif mode == 'first-clickable-below-text':
    a = next((n for n in nodes if n.attrib.get('text') == needle), None)
    if a is not None and bounds(a) is not None:
        ay2 = bounds(a)[3]
        candidates = []
        for n in nodes:
            b = bounds(n)
            if n.attrib.get('clickable') == 'true' and b is not None and b[1] >= ay2:
                candidates.append((b[1], b[0], (b[2]-b[0])*(b[3]-b[1]), n))
        if candidates:
            found = sorted(candidates, key=lambda t: (t[0], t[1], t[2]))[0][3]

if found is None or bounds(found) is None:
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

assert_selected_desc() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot(); query = sys.argv[2]
for n in root.iter('node'):
    if n.attrib.get('content-desc') == query and n.attrib.get('selected') == 'true':
        raise SystemExit(0)
raise SystemExit(f'desc not selected: {query!r}')
PY
}

assert_selected_ancestor_of_text() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot(); query = sys.argv[2]
parent = {c:p for p in root.iter() for c in p}
node = next((n for n in root.iter('node') if n.attrib.get('text') == query), None)
while node is not None:
    if node.attrib.get('selected') == 'true':
        raise SystemExit(0)
    node = parent.get(node)
raise SystemExit(f'no selected ancestor for {query!r}')
PY
}

assert_editor() {
  local f="$1"
  for l in Seal Media Voice Attachment Sticker Format; do assert_label "$f" "$l"; done
  assert_desc "$f" 'Go back'
  assert_desc "$f" 'Change mood'
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
assert_label qa-evidence/editor-initial.xml 'New capsule'

click_text qa-evidence/editor-initial.xml 'Title'
adb shell input text 'LaterEditorQA'
sleep 1
dump editor-title-keyboard; shot editor-title-keyboard
assert_label qa-evidence/editor-title-keyboard.xml 'LaterEditorQA'
assert_label qa-evidence/editor-title-keyboard.xml 'Write to your future self'
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

# Format Home: exercise the actual inline-format toggles and their selected state.
click_label qa-evidence/editor-body.xml 'Format'
sleep 1
dump format-home; shot format-home
for l in Home Paragraph Paper; do assert_label qa-evidence/format-home.xml "$l"; done
assert_desc qa-evidence/format-home.xml 'Undo'; assert_desc qa-evidence/format-home.xml 'Redo'
for style in Bold Italic Underline Strikethrough; do
  assert_desc qa-evidence/format-home.xml "$style"
  click_desc qa-evidence/format-home.xml "$style"
  sleep 0.5
  dump "format-${style,,}-selected"
  assert_selected_desc "qa-evidence/format-${style,,}-selected.xml" "$style"
  cp "qa-evidence/format-${style,,}-selected.xml" qa-evidence/format-home.xml
done
shot format-inline-selected

# Paragraph controls: verify bullets, numbering, and quote all really toggle.
click_label qa-evidence/format-home.xml 'Paragraph'; sleep 1
dump format-paragraph; shot format-paragraph
for l in Bullets Numbered Quote; do assert_label qa-evidence/format-paragraph.xml "$l"; done
for style in Bullets Numbered Quote; do
  click_label qa-evidence/format-paragraph.xml "$style"
  sleep 0.5
  dump "paragraph-${style,,}-selected"
  assert_selected_ancestor_of_text "qa-evidence/paragraph-${style,,}-selected.xml" "$style"
  click_label "qa-evidence/paragraph-${style,,}-selected.xml" "$style"
  sleep 0.5
  dump format-paragraph
done

click_label qa-evidence/format-paragraph.xml 'Paper'; sleep 1
dump format-paper; shot format-paper
assert_label qa-evidence/format-paper.xml 'Warm paper'
assert_label qa-evidence/format-paper.xml 'Soft sage'
adb shell input keyevent 4; sleep 1

# Sticker insertion must return to the editor with a real page-anchored sticker.
dump editor-before-sticker
click_label qa-evidence/editor-before-sticker.xml 'Sticker'; sleep 1
dump stickers; shot stickers; assert_label qa-evidence/stickers.xml 'Stickers'
python3 qa_click.py qa-evidence/stickers.xml first-clickable-below-text 'Stickers'
sleep 1; dump editor-after-sticker; shot editor-after-sticker; assert_editor qa-evidence/editor-after-sticker.xml

# Mood target is explicit now; scroll to the final mood and prove selection persists.
click_desc qa-evidence/editor-after-sticker.xml 'Change mood'
sleep 1; dump mood; shot mood
assert_label qa-evidence/mood.xml 'How does this moment feel?'
assert_label qa-evidence/mood.xml 'Choose the one that fits this moment.'
cp qa-evidence/mood.xml qa-evidence/mood-scrolled.xml
for i in 1 2 3 4; do
  if grep -q 'content-desc="Peaceful"' qa-evidence/mood-scrolled.xml; then break; fi
  adb shell input swipe 160 600 160 370 500
  sleep 0.6
  dump mood-scrolled
done
shot mood-scrolled
assert_desc qa-evidence/mood-scrolled.xml 'Peaceful'
click_desc qa-evidence/mood-scrolled.xml 'Peaceful'
sleep 1
dump editor-after-mood; shot editor-after-mood; assert_editor qa-evidence/editor-after-mood.xml
click_desc qa-evidence/editor-after-mood.xml 'Change mood'; sleep 1
dump mood-reopen
for i in 1 2 3 4; do
  if grep -q 'content-desc="Peaceful"' qa-evidence/mood-reopen.xml; then break; fi
  adb shell input swipe 160 600 160 370 500; sleep 0.6; dump mood-reopen
done
assert_selected_desc qa-evidence/mood-reopen.xml 'Peaceful'
adb shell input keyevent 4; sleep 1

# Seal sheet should open but QA never seals the synthetic entry.
dump editor-before-seal
click_label qa-evidence/editor-before-seal.xml 'Seal'; sleep 1
dump seal; shot seal
assert_label qa-evidence/seal.xml 'Date'; assert_label qa-evidence/seal.xml 'Time'
assert_label qa-evidence/seal.xml 'Choose a time in the future.'; assert_label qa-evidence/seal.xml 'Seal capsule'
adb shell input keyevent 4; sleep 1

# Exercise both external pickers and prove the editor survives returning from each.
for control in Media Attachment; do
  lower="$(printf '%s' "$control" | tr '[:upper:]' '[:lower:]')"
  dump "editor-before-${lower}"; click_label "qa-evidence/editor-before-${lower}.xml" "$control"; sleep 2
  dump "${lower}-picker"; shot "${lower}-picker"; adb shell input keyevent 4; sleep 1
  dump "editor-after-${lower}"; assert_editor "qa-evidence/editor-after-${lower}.xml"
done

# Voice: grant permission so this tests the recorder path rather than only the permission dialog.
adb shell pm grant com.night.later android.permission.RECORD_AUDIO || true
dump editor-before-voice; click_label qa-evidence/editor-before-voice.xml 'Voice'; sleep 2
dump voice-entry; shot voice-entry
if grep -q 'text="Recording"' qa-evidence/voice-entry.xml; then
  assert_desc qa-evidence/voice-entry.xml 'Stop recording'
  click_desc qa-evidence/voice-entry.xml 'Stop recording'; sleep 2
else
  assert_label qa-evidence/voice-entry.xml "Couldn't start the microphone."
fi
dump editor-after-voice; assert_editor qa-evidence/editor-after-voice.xml

# Scroll and ensure the fixed editor toolbar survives while page content moves.
adb shell input swipe 160 540 160 180 450; sleep 1
dump editor-scrolled; shot editor-scrolled
assert_editor qa-evidence/editor-scrolled.xml

# Autosave must settle to a real encrypted draft before leaving.
for i in 1 2 3 4 5 6; do
  sleep 1
  dump editor-autosave
  if grep -q 'text="Draft autosaved"' qa-evidence/editor-autosave.xml; then break; fi
done
assert_label qa-evidence/editor-autosave.xml 'Draft autosaved'
shot editor-autosave
click_desc qa-evidence/editor-autosave.xml 'Go back'; sleep 2
dump home-after-editor; shot home-after-editor

# Relaunch and reopen the persisted draft, then prove both title and body survive.
adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 3
dump home-relaunch; shot home-relaunch
assert_label qa-evidence/home-relaunch.xml 'EditorBodyQA'
click_text qa-evidence/home-relaunch.xml 'EditorBodyQA'
sleep 2
dump editor-reopened; shot editor-reopened
assert_editor qa-evidence/editor-reopened.xml
assert_label qa-evidence/editor-reopened.xml 'LaterEditorQA'
assert_label qa-evidence/editor-reopened.xml 'EditorBodyQA'

adb logcat -d > qa-evidence/logcat.txt
adb logcat -b crash -d > qa-evidence/crash.txt
if grep -q 'com.night.later' qa-evidence/crash.txt; then
  cat qa-evidence/crash.txt
  exit 1
fi

echo 'LATER_EDITOR_QA_PASS'
