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
  local ok=0
  for attempt in 1 2 3 4 5; do
    adb shell rm -f /sdcard/later-window.xml >/dev/null 2>&1 || true
    if adb shell uiautomator dump /sdcard/later-window.xml >/dev/null 2>&1 && \
       adb shell test -s /sdcard/later-window.xml; then
      ok=1
      break
    fi
    sleep 1
  done
  if [ "$ok" -ne 1 ]; then
    echo "uiautomator dump failed for $name" >&2
    exit 1
  fi
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
  assert_desc "$f" 'Change mood'
}

collect_evidence() {
  adb logcat -d > qa-evidence/logcat.txt 2>/dev/null || true
  adb logcat -b crash -d > qa-evidence/crash.txt 2>/dev/null || true
  adb logcat -d -s LaterFormatQA:I > qa-evidence/format-log.txt 2>/dev/null || true
}
trap collect_evidence EXIT

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
sleep 1
adb shell input text 'LaterEditorQA'
sleep 1
dump editor-title-keyboard
if ! grep -q 'text="LaterEditorQA"' qa-evidence/editor-title-keyboard.xml; then
  echo 'retrying title text after IME/focus settle'
  adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
  sleep 0.3
  adb shell input text 'LaterEditorQA'
  sleep 1
  dump editor-title-keyboard
fi
shot editor-title-keyboard
assert_label qa-evidence/editor-title-keyboard.xml 'LaterEditorQA'
assert_label qa-evidence/editor-title-keyboard.xml 'Write to your future self'
adb shell input keyevent 4; sleep 1

dump editor-pre-body
click_contains qa-evidence/editor-pre-body.xml 'Write to your future self'
sleep 1
adb shell input text 'EditorBodyQA'
sleep 1
dump editor-body-keyboard
if ! grep -q 'text="EditorBodyQA"' qa-evidence/editor-body-keyboard.xml; then
  echo 'retrying body text after IME/focus settle'
  adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
  sleep 0.3
  adb shell input text 'EditorBodyQA'
  sleep 1
  dump editor-body-keyboard
fi
shot editor-body-keyboard
assert_label qa-evidence/editor-body-keyboard.xml 'EditorBodyQA'
assert_editor qa-evidence/editor-body-keyboard.xml

# Keep the real editor focus and selected body range alive while Format opens.
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
sleep 0.6
dump editor-body-selected; shot editor-body-selected
click_label qa-evidence/editor-body-selected.xml 'Format'; sleep 1

dump format-home; shot format-home
for l in Home Paragraph Paper; do assert_label qa-evidence/format-home.xml "$l"; done
assert_desc qa-evidence/format-home.xml 'Undo'; assert_desc qa-evidence/format-home.xml 'Redo'
for style in Bold Italic Underline Strikethrough; do
  assert_desc qa-evidence/format-home.xml "$style"
  click_desc qa-evidence/format-home.xml "$style"
  sleep 0.5
  dump "format-${style,,}-selected"
  cp "qa-evidence/format-${style,,}-selected.xml" qa-evidence/format-home.xml
  adb logcat -d -s LaterFormatQA:I > qa-evidence/format-log.txt 2>/dev/null || true
done
shot format-inline-selected

click_label qa-evidence/format-home.xml 'Paragraph'; sleep 1
dump format-paragraph; shot format-paragraph
for l in Bullets Numbered Quote; do assert_label qa-evidence/format-paragraph.xml "$l"; done
for style in Bullets Numbered Quote; do
  click_label qa-evidence/format-paragraph.xml "$style"
  sleep 0.5
  dump "paragraph-${style,,}-selected"
  adb logcat -d -s LaterFormatQA:I > qa-evidence/format-log.txt 2>/dev/null || true
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

# Mood selection must persist after reopening the sheet.
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
  adb shell input swipe 160 600 160 370 500
  sleep 0.6
  dump mood-reopen
done
grep -q 'content-desc="Peaceful".*checked="true"' qa-evidence/mood-reopen.xml || { cat qa-evidence/mood-reopen.xml; exit 1; }
adb shell input keyevent 4; sleep 0.7
dump mood-after-first-back
if grep -q 'How does this moment feel?' qa-evidence/mood-after-first-back.xml; then
  adb shell input keyevent 4; sleep 1
fi
dump editor-after-mood-close
assert_editor qa-evidence/editor-after-mood-close.xml

# Seal sheet: presets first, lower controls reachable by scrolling on 320x640.
dump editor-before-seal
click_label qa-evidence/editor-before-seal.xml 'Seal'; sleep 1
dump seal; shot seal
assert_label qa-evidence/seal.xml 'When should this return?'
assert_label qa-evidence/seal.xml 'Choose when your future self gets access.'
for l in Tomorrow '1 week' '1 month' '1 year'; do assert_label qa-evidence/seal.xml "$l"; done
cat qa-evidence/seal.xml > qa-evidence/seal-scroll-history.txt
cp qa-evidence/seal.xml qa-evidence/seal-scrolled.xml
for i in 1 2 3 4; do
  if grep -q 'text="Seal capsule"' qa-evidence/seal-scrolled.xml; then break; fi
  adb shell input swipe 160 600 160 360 500
  sleep 0.6
  dump seal-scrolled
  cat qa-evidence/seal-scrolled.xml >> qa-evidence/seal-scroll-history.txt
done
shot seal-scrolled
grep -q 'text="Date"' qa-evidence/seal-scroll-history.txt
grep -q 'text="Time"' qa-evidence/seal-scroll-history.txt
grep -q "You won't be able to open or edit this capsule until then." qa-evidence/seal-scroll-history.txt
grep -q 'text="Seal capsule"' qa-evidence/seal-scroll-history.txt
adb shell input keyevent 4; sleep 0.7
dump seal-after-first-back
if grep -q 'When should this return?' qa-evidence/seal-after-first-back.xml; then
  adb shell input keyevent 4; sleep 1
fi
dump editor-after-seal
assert_editor qa-evidence/editor-after-seal.xml

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
assert_label qa-evidence/editor-after-voice.xml 'EditorBodyQA'

# The editor still owns focus after rich-text/voice interactions. Dismiss the IME before
# sending a page scroll so Gboard cannot interpret the swipe as glide typing.
adb shell input keyevent 4; sleep 1
dump editor-before-scroll; shot editor-before-scroll
assert_editor qa-evidence/editor-before-scroll.xml
assert_label qa-evidence/editor-before-scroll.xml 'EditorBodyQA'
adb shell input swipe 160 500 160 180 450; sleep 1
dump editor-scrolled; shot editor-scrolled
assert_editor qa-evidence/editor-scrolled.xml
assert_label qa-evidence/editor-scrolled.xml 'EditorBodyQA'

# Autosave must settle to a real encrypted draft before leaving.
for i in 1 2 3 4 5 6; do
  sleep 1
  dump editor-autosave
  if grep -q 'text="Draft autosaved"' qa-evidence/editor-autosave.xml; then break; fi
done
assert_label qa-evidence/editor-autosave.xml 'Draft autosaved'
assert_label qa-evidence/editor-autosave.xml 'LaterEditorQA'
assert_label qa-evidence/editor-autosave.xml 'EditorBodyQA'
shot editor-autosave
click_desc qa-evidence/editor-autosave.xml 'Go back'; sleep 2
dump home-after-editor; shot home-after-editor
assert_label qa-evidence/home-after-editor.xml 'EditorBodyQA'

# Relaunch and reopen the persisted draft. Existing Home behavior previews first body text.
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
