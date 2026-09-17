from pathlib import Path

path = Path('sora-ci/ui-smoke.sh')
text = path.read_text()

helper_marker = '''tap_text() {
  local label="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    dismiss_system_dialogs
    if tap_text_once "$label"; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  if tap_root_tab_fallback "$label"; then
    return 0
  fi
  echo "Could not find UI node: $label" >&2
  log_ui_state "missing node $label"
  shot "failure-${label//[^A-Za-z0-9]/_}"
  return 1
}
'''
helper_replacement = helper_marker + r'''
node_exists() {
  local label="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try:
    root=ET.parse('/tmp/window.xml').getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter('node'):
    if (node.attrib.get('text') or '').strip() == label or (node.attrib.get('content-desc') or '').strip() == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1"
  local timeout="${2:-20}"
  local elapsed=0
  while (( elapsed < timeout )); do
    dismiss_system_dialogs
    if node_exists "$label"; then
      echo "UI node '$label' available after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "Timed out waiting for UI node: $label" >&2
  log_ui_state "missing node $label"
  shot "failure-wait-${label//[^A-Za-z0-9]/_}"
  return 1
}

tap_first_music_tile() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/window.xml').getroot()
choices=[]
for node in root.iter('node'):
    if node.attrib.get('clickable') != 'true':
        continue
    bounds=node.attrib.get('bounds','')
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    width=x2-x1; height=y2-y1
    # Music quick-grid cards on the Pixel 7 smoke viewport. Avoid tabs/FAB/nav.
    if 850 <= y1 <= 1500 and 350 <= width <= 600 and 90 <= height <= 240:
        choices.append((y1,x1,x2,y2))
if not choices:
    raise SystemExit('No playable Music tile found in expected viewport')
y1,x1,x2,y2=sorted(choices)[0]
x=(x1+x2)//2; y=(y1+y2)//2
print(f'Tapping first Music tile at {x},{y}')
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}
'''

if 'wait_for_node() {' not in text:
    if helper_marker not in text:
        raise SystemExit('tap_text helper marker not found')
    text = text.replace(helper_marker, helper_replacement, 1)

old_music = '''tap_text 'Movies & TV'
tap_text Music
sleep 5
shot 06-music-home

tap_text Library
'''
new_music = '''tap_text 'Movies & TV'
tap_text Music
sleep 5
shot 06-music-home

tap_first_music_tile
wait_for_node 'Mini player' 20
wait_for_node Pause 20
shot 06a-music-playing

tap_text 'Mini player'
wait_for_node Pause 10
shot 06b-music-full-player

tap_text Pause
wait_for_node Play 8
tap_text Play
wait_for_node Pause 15
tap_text Next
wait_for_node Pause 20
sleep 2
tap_text Previous
wait_for_node Pause 20
shot 06c-music-transport
adb shell input keyevent KEYCODE_BACK
sleep 2

tap_text Library
'''
if new_music not in text:
    if old_music not in text:
        raise SystemExit('music smoke section marker not found')
    text = text.replace(old_music, new_music, 1)

path.write_text(text)
print('Applied strict Sora Music playback smoke.')
