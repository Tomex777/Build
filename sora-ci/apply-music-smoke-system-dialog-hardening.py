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

wait_marker = '''  while (( elapsed < timeout )); do
    if node_exists "$label"; then'''
if text.count(wait_marker) != 1:
    raise SystemExit('wait loop marker mismatch')
text = text.replace(
    wait_marker,
    '''  while (( elapsed < timeout )); do
    dismiss_system_dialogs
    if node_exists "$label"; then''',
    1,
)

for function_name in ('tap_text', 'rapid_double_tap_text'):
    function_marker = f'''{function_name}() {{
  local label="$1"
'''
    if text.count(function_marker) != 1:
        raise SystemExit(f'{function_name} marker mismatch')
    text = text.replace(
        function_marker,
        function_marker + '  dismiss_system_dialogs\n',
        1,
    )

media_marker = '''tap_media_tab() {
'''
if text.count(media_marker) != 1:
    raise SystemExit('media tab marker mismatch')
text = text.replace(media_marker, media_marker + '  dismiss_system_dialogs\n', 1)

start_marker = '''sleep 5

shot 00-home
'''
if text.count(start_marker) != 1:
    raise SystemExit('initial dismiss marker mismatch')
text = text.replace(
    start_marker,
    '''sleep 5
# Hosted Pixel images occasionally surface a Pixel Launcher ANR over Sora.
# Dismiss system UI before the first app assertion; this is not a Sora process failure.
dismiss_system_dialogs

shot 00-home
''',
    1,
)

background_marker = '''# Background Media3 service/session must stay alive.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
'''
replacement = '''# Background Media3 service/session must stay alive. Use Settings rather than the
# emulator launcher so a Pixel Launcher ANR cannot contaminate Sora's playback test.
adb shell am start -W -a android.settings.SETTINGS >/dev/null
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
'''
if text.count(background_marker) != 1:
    raise SystemExit('background transport marker mismatch')
text = text.replace(background_marker, replacement, 1)

path.write_text(text)
