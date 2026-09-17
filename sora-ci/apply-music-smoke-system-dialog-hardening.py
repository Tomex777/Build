from pathlib import Path

path = Path('sora-ci/music-deterministic-smoke.sh')
text = path.read_text()

marker = '''dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/sora-music-window.xml >/dev/null 2>&1 || true
}
'''
addition = marker + r'''

dismiss_system_dialogs() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
try:
    root=ET.parse('/tmp/sora-music-window.xml').getroot()
except Exception:
    raise SystemExit(0)
# Prefer keeping the system process alive; fall back to acknowledging OK dialogs.
for label in ('Wait', 'OK'):
    for node in root.iter('node'):
        text=(node.attrib.get('text') or '').strip()
        desc=(node.attrib.get('content-desc') or '').strip()
        if text != label and desc != label:
            continue
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        subprocess.call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        raise SystemExit(0)
PY
  adb shell am start -W -n com.night.sora/.MainActivity >/dev/null 2>&1 || true
  sleep 1
}
'''
if text.count(marker) != 1:
    raise SystemExit('dump_ui marker mismatch')
text = text.replace(marker, addition, 1)

old_wait = '''  while (( elapsed < timeout )); do
    if node_exists "$label"; then'''
new_wait = '''  while (( elapsed < timeout )); do
    dismiss_system_dialogs
    if node_exists "$label"; then'''
if text.count(old_wait) != 1:
    raise SystemExit('wait loop marker mismatch')
text = text.replace(old_wait, new_wait, 1)

old_tap = '''tap_text() {
  local label="$1"
  dump_ui
'''
new_tap = '''tap_text() {
  local label="$1"
  dismiss_system_dialogs
  dump_ui
'''
if text.count(old_tap) != 1:
    raise SystemExit('tap_text marker mismatch')
text = text.replace(old_tap, new_tap, 1)

old_double = '''rapid_double_tap_text() {
  local label="$1"
  dump_ui
'''
new_double = '''rapid_double_tap_text() {
  local label="$1"
  dismiss_system_dialogs
  dump_ui
'''
if text.count(old_double) != 1:
    raise SystemExit('rapid double marker mismatch')
text = text.replace(old_double, new_double, 1)

old_media = '''tap_media_tab() {
  if node_exists Media; then'''
new_media = '''tap_media_tab() {
  dismiss_system_dialogs
  if node_exists Media; then'''
if text.count(old_media) != 1:
    raise SystemExit('media tab marker mismatch')
text = text.replace(old_media, new_media, 1)

old_start = '''sleep 5

shot 00-home
'''
new_start = '''sleep 5
# Hosted Pixel images occasionally surface a Pixel Launcher ANR over Sora.
# Dismiss system UI before the first app assertion; this is not a Sora process failure.
dismiss_system_dialogs

shot 00-home
'''
if text.count(old_start) != 1:
    raise SystemExit('initial dismiss marker mismatch')
text = text.replace(old_start, new_start, 1)

old_background = '''# Background Media3 service/session must stay alive.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
'''
new_background = '''# Background Media3 service/session must stay alive. Use Settings rather than the
# emulator launcher so a Pixel Launcher ANR cannot contaminate Sora's playback test.
adb shell am start -W -a android.settings.SETTINGS >/dev/null
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
'''
if text.count(old_background) != 1:
    raise SystemExit('background transport marker mismatch')
text = text.replace(old_background, new_background, 1)

path.write_text(text)
