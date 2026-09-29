#!/usr/bin/env bash
set -euo pipefail

cd later
mkdir -p qa-evidence

python3 - <<'PY'
import struct, zlib
from pathlib import Path
w, h = 320, 200
rows = []
for y in range(h):
    row = bytearray([0])
    for x in range(w):
        check = ((x // 20) + (y // 20)) % 2
        row.extend((28 + x * 180 // w, 80 + y * 130 // h, 220 if check else 100, 0 if check and x < 80 else 255))
    rows.append(bytes(row))
def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n'
png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(b''.join(rows), 9))
png += chunk(b'IEND', b'')
Path('qa-evidence/LaterQAImage.png').write_bytes(png)
PY

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'testsrc=size=320x240:rate=20' \
  -f lavfi -i 'sine=frequency=660:sample_rate=44100' \
  -t 20 -c:v libx264 -profile:v baseline -preset ultrafast -pix_fmt yuv420p \
  -c:a aac -b:a 64k -movflags +faststart qa-evidence/LaterQAVideo.mp4

adb shell mkdir -p /sdcard/Pictures/LaterQA /sdcard/Movies/LaterQA
adb push qa-evidence/LaterQAImage.png /sdcard/Pictures/LaterQA/LaterQAImage.png >/dev/null
adb push qa-evidence/LaterQAVideo.mp4 /sdcard/Movies/LaterQA/LaterQAVideo.mp4 >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/LaterQA/LaterQAImage.png >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/LaterQA/LaterQAVideo.mp4 >/dev/null
sleep 2

# Reopen the persisted draft from the editor QA run.
dump() {
  local name="$1" ok=0
  for attempt in 1 2 3 4 5; do
    adb shell rm -f /sdcard/later-window.xml >/dev/null 2>&1 || true
    if timeout 12s adb shell uiautomator dump --compressed /sdcard/later-window.xml >/dev/null 2>&1 && adb shell test -s /sdcard/later-window.xml; then ok=1; break; fi
    sleep 1
  done
  if [ "$ok" -ne 1 ]; then
    echo "uiautomator dump failed: $name" >&2
    timeout 8s adb exec-out screencap -p > "qa-evidence/${name}-dump-failure.png" 2>/dev/null || true
    timeout 8s adb shell dumpsys activity top > "qa-evidence/${name}-activity.txt" 2>&1 || true
    timeout 8s adb shell dumpsys window > "qa-evidence/${name}-window.txt" 2>&1 || true
    timeout 8s adb logcat -d -v threadtime > "qa-evidence/${name}-logcat.txt" 2>&1 || true
    exit 1
  fi
  adb pull /sdcard/later-window.xml "qa-evidence/${name}.xml" >/dev/null
}
shot() { adb exec-out screencap -p > "qa-evidence/$1.png"; }
click_label() { python3 qa_click.py "$1" label-exact "$2"; }
click_text() { python3 qa_click.py "$1" text-exact "$2"; }
click_contains() { python3 qa_click.py "$1" text-contains "$2"; }
click_desc() { python3 qa_click.py "$1" desc-exact "$2"; }
assert_label() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); q=sys.argv[2]
if not any(q in n.attrib.get('text','') or q in n.attrib.get('content-desc','') for n in root.iter('node')):
    raise SystemExit(f'missing {q!r} in {sys.argv[1]}')
PY
}
video_progress_seconds() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
node = next((n for n in root.iter('node') if n.attrib.get('resource-id','').endswith(':id/exo_progress')), None)
if node is None:
    raise SystemExit(f'no Media3 progress node in {sys.argv[1]}')
value = (node.attrib.get('content-desc') or node.attrib.get('text') or '').strip()
m = re.fullmatch(r'(?:(\d+):)?(\d{1,2}):(\d{2})', value)
if not m:
    raise SystemExit(f'unparseable Media3 progress {value!r} in {sys.argv[1]}')
print(int(m.group(1) or 0) * 3600 + int(m.group(2)) * 60 + int(m.group(3)))
PY
}

seek_video_progress_semantically() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
node = next((n for n in root.iter('node') if n.attrib.get('resource-id','').endswith(':id/exo_progress')), None)
if node is None:
    raise SystemExit(f'no Media3 progress node in {sys.argv[1]}')
m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
if not m:
    raise SystemExit('Media3 progress node has no usable bounds')
x1, y1, x2, y2 = map(int, m.groups())
x = x1 + round((x2 - x1) * 0.35)
y = (y1 + y2) // 2
print(f"seek Media3 progress semantically at {x},{y}")
subprocess.run(['adb','shell','input','tap',str(x),str(y)], check=True)
PY
}

tap_media3_control_from_reference() {
  local xml="$1" id_suffix="$2" action="$3"
  python3 - "$xml" "$id_suffix" "$action" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
path, suffix, action = sys.argv[1], sys.argv[2], sys.argv[3]
root = ET.parse(path).getroot()
node = next((n for n in root.iter('node') if n.attrib.get('resource-id','').endswith(suffix)), None)
if node is None:
    raise SystemExit(f'no Media3 control {suffix!r} in {path}')
m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
if not m:
    raise SystemExit(f'Media3 control {suffix!r} has no usable bounds')
x1, y1, x2, y2 = map(int, m.groups())
x, y = (x1 + x2) // 2, (y1 + y2) // 2
print(f"{action} at {x},{y} from semantic control {suffix}")
subprocess.run(['adb','shell','input','tap',str(x),str(y)], check=True)
PY
}

assert_media_indexed() {
  local uri="$1" name="$2"
  for attempt in 1 2 3 4 5 6; do
    if adb shell content query --uri "$uri" --projection _display_name 2>/dev/null | tr -d '\r' | grep -Fq "$name"; then
      return 0
    fi
    sleep 1
  done
  echo "MediaStore did not index deterministic fixture $name" >&2
  adb shell content query --uri "$uri" --projection _display_name 2>/dev/null || true
  exit 1
}

media_block_desc() {
  local xml="$1" kind="$2"
  python3 - "$xml" "$kind" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, kind = sys.argv[1], sys.argv[2]
extensions = {
    'image': ('.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'),
    'video': ('.mp4', '.m4v', '.mov', '.webm', '.mkv', '.3gp'),
}[kind]
root = ET.parse(path).getroot()
candidates = []
for node in root.iter('node'):
    desc = node.attrib.get('content-desc', '').strip()
    if not desc.lower().endswith(extensions):
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    candidates.append(((x2-x1)*(y2-y1), desc))
if not candidates:
    raise SystemExit(f'no {kind} media block found in {path}')
print(max(candidates)[1])
PY
}

click_media_block() {
  local xml="$1" kind="$2"
  python3 - "$xml" "$kind" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
path, kind = sys.argv[1], sys.argv[2]
extensions = {
    'image': ('.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'),
    'video': ('.mp4', '.m4v', '.mov', '.webm', '.mkv', '.3gp'),
}[kind]
root = ET.parse(path).getroot()
candidates = []
for node in root.iter('node'):
    desc = node.attrib.get('content-desc', '').strip()
    if not desc.lower().endswith(extensions):
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    candidates.append(((x2-x1)*(y2-y1), desc, x1, y1, x2, y2))
if not candidates:
    raise SystemExit(f'no {kind} media block found in {path}')
_, desc, x1, y1, x2, y2 = max(candidates)
x, y = (x1+x2)//2, (y1+y2)//2
print(f"click {kind} media block {desc!r} at {x},{y}")
subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)], check=True)
PY
}

ensure_media_visible() {
  local name="$1" kind="$2"
  for attempt in 1 2 3 4 5 6; do
    dump "$name"
    if media_block_desc "qa-evidence/${name}.xml" "$kind" >/dev/null 2>&1; then
      shot "$name"
      return 0
    fi
    adb shell input swipe 160 540 160 260 450
    sleep 0.7
  done
  cat "qa-evidence/${name}.xml"
  echo "Could not scroll a $kind media block into the editor viewport" >&2
  exit 1
}

click_picker_media_kind() {
  local xml="$1" kind="$2"
  python3 - "$xml" "$kind" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
path, kind = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
prefix = kind + ' taken on '
for node in root.iter('node'):
    desc = node.attrib.get('content-desc', '')
    if not desc.startswith(prefix):
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    x, y = (x1+x2)//2, (y1+y2)//2
    subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)], check=True)
    print(f"click picker semantic {kind!r} at {x},{y} desc={desc!r}")
    raise SystemExit(0)
raise SystemExit(1)
PY
}

select_fixture() {
  local name="$1" category="$2" prefix="$3" kind="$4"
  dump "${prefix}-picker-open"; shot "${prefix}-picker-open"

  # Android 16's Photo Picker may intentionally omit source filenames from the
  # accessibility tree. Prefer the filename when exposed; otherwise select by
  # the native Photo/Video semantic node after MediaStore proved the exact
  # deterministic fixture is indexed.
  local current="qa-evidence/${prefix}-picker-open.xml"
  local selected=0
  for attempt in 1 2 3 4 5 6; do
    if grep -Fq "$name" "$current"; then
      click_contains "$current" "$name"
      selected=1
      break
    fi
    if click_picker_media_kind "$current" "$kind"; then
      selected=1
      break
    fi
    if [ "$attempt" -eq 1 ] && grep -q "text=\"${category}\"" "$current"; then
      click_label "$current" "$category"
      sleep 1
    else
      adb shell input swipe 180 570 180 300 400
      sleep 0.5
    fi
    dump "${prefix}-picker-open"
    current="qa-evidence/${prefix}-picker-open.xml"
  done
  [ "$selected" -eq 1 ] || {
    cat "$current"
    echo "Photo Picker exposed neither filename $name nor a $kind media node" >&2
    exit 1
  }

  sleep 1
  dump "${prefix}-picker-selected"; shot "${prefix}-picker-selected"
  for cta in Add Done Select; do
    if grep -q "text=\"[^\"]*${cta}[^\"]*\"" "qa-evidence/${prefix}-picker-selected.xml"; then
      click_contains "qa-evidence/${prefix}-picker-selected.xml" "$cta"
      break
    fi
  done
  sleep 3
}

assert_media_indexed content://media/external/images/media LaterQAImage.png
assert_media_indexed content://media/external/video/media LaterQAVideo.mp4

# Image viewer: open, double-tap zoom, edit/rotate, update capsule, and reopen original.
dump media-editor-start
assert_label qa-evidence/media-editor-start.xml 'Media'
click_label qa-evidence/media-editor-start.xml 'Media'; sleep 2
select_fixture LaterQAImage.png Photos image Photo

dump image-attached; shot image-attached
image_source_name="$(media_block_desc qa-evidence/image-attached.xml image)"
echo "Attached deterministic image as provider display name: $image_source_name"
click_media_block qa-evidence/image-attached.xml image; sleep 2
dump image-viewer; shot image-viewer
assert_label qa-evidence/image-viewer.xml 'Close image'
assert_label qa-evidence/image-viewer.xml 'Share'
assert_label qa-evidence/image-viewer.xml 'Edit'
adb shell input tap 180 350; adb shell input tap 180 350; sleep 1
shot image-viewer-zoomed
click_label qa-evidence/image-viewer.xml 'Edit'; sleep 2
dump image-editor; shot image-editor
for label in 'Edit image' Crop Adjust Filter Rotate; do assert_label qa-evidence/image-editor.xml "$label"; done
click_label qa-evidence/image-editor.xml 'Rotate'; sleep 0.5
dump image-editor-rotated; shot image-editor-rotated
assert_label qa-evidence/image-editor-rotated.xml 'Update capsule'
click_label qa-evidence/image-editor-rotated.xml 'Update capsule'; sleep 4
image_rel="$(adb shell run-as com.night.later find cache/media_drafts -type f -name 'edited_*' | tr -d '\r' | head -n1)"
[ -n "$image_rel" ] || { echo 'No edited image copy found in app-private cache' >&2; exit 1; }
image_ext="${image_rel##*.}"
image_output="qa-evidence/edited-image-output.${image_ext}"
adb exec-out run-as com.night.later cat "$image_rel" > "$image_output"
[ -s "$image_output" ] || { echo 'Edited image output is empty' >&2; exit 1; }
ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height -of default=noprint_wrappers=1 "$image_output" > qa-evidence/edited-image-probe.txt
python3 - <<'PY'
probe = {}
for line in open('qa-evidence/edited-image-probe.txt'):
    if '=' in line:
        k, v = line.strip().split('=', 1)
        probe[k] = v
codec = probe.get('codec_name')
if codec not in {'mjpeg', 'png', 'webp'}:
    raise SystemExit(f'unsupported edited image codec: {codec!r}')
if (probe.get('width'), probe.get('height')) != ('200', '320'):
    raise SystemExit(f"rotated image dimensions changed unexpectedly: {probe.get('width')}x{probe.get('height')}")
PY
dump image-edited-attached; shot image-edited-attached
image_edited_name="$(media_block_desc qa-evidence/image-edited-attached.xml image)"
case "$image_edited_name" in
  *_edited.*) ;;
  *) echo "Edited image block was not version-labelled: $image_edited_name" >&2; exit 1 ;;
esac
click_media_block qa-evidence/image-edited-attached.xml image; sleep 2
dump image-edited-viewer; shot image-edited-viewer
assert_label qa-evidence/image-edited-viewer.xml 'Original'
click_label qa-evidence/image-edited-viewer.xml 'Original'; sleep 1
dump image-original-viewer; shot image-original-viewer
assert_label qa-evidence/image-original-viewer.xml 'Edited'
click_label qa-evidence/image-original-viewer.xml 'Close image'; sleep 1

# Video viewer/edit/export: play beyond zero, seek, trim, export and play the real output.
dump editor-before-video
click_label qa-evidence/editor-before-video.xml 'Media'; sleep 2
select_fixture LaterQAVideo.mp4 Videos video Video

ensure_media_visible video-attached video
video_source_name="$(media_block_desc qa-evidence/video-attached.xml video)"
echo "Attached deterministic video as provider display name: $video_source_name"
click_media_block qa-evidence/video-attached.xml video; sleep 2
dump video-viewer; shot video-viewer
assert_label qa-evidence/video-viewer.xml 'Exit fullscreen'
assert_label qa-evidence/video-viewer.xml 'Edit'
assert_label qa-evidence/video-viewer.xml 'Mute'
click_label qa-evidence/video-viewer.xml '1.0×'; sleep 0.5
dump video-speed; assert_label qa-evidence/video-speed.xml '1.5×'
click_label qa-evidence/video-speed.xml 'Mute'; sleep 0.5
dump video-muted; assert_label qa-evidence/video-muted.xml 'Unmute'
click_label qa-evidence/video-muted.xml 'Unmute'; sleep 0.5
# Start playback, then pause it through the real Media3 controller. Android 16
# uiautomator dumps are slow enough for PlayerView's controller to auto-hide
# while playback continues, so handle both the visible and hidden-controller states.
dump video-ready-to-play
assert_label qa-evidence/video-ready-to-play.xml 'Play'
click_desc qa-evidence/video-ready-to-play.xml 'Play'
sleep 1
dump video-viewer-playing; shot video-viewer-playing

if grep -q 'content-desc="Pause"' qa-evidence/video-viewer-playing.xml; then
  click_desc qa-evidence/video-viewer-playing.xml 'Pause'
elif grep -q 'content-desc="Show player controls"' qa-evidence/video-viewer-playing.xml; then
  click_desc qa-evidence/video-viewer-playing.xml 'Show player controls'
  sleep 0.25
  tap_media3_control_from_reference qa-evidence/video-ready-to-play.xml ':id/exo_play_pause' 'pause Media3 playback'
else
  cat qa-evidence/video-viewer-playing.xml
  echo 'Media3 player exposed neither Pause nor Show player controls while playing' >&2
  exit 1
fi

# Once paused, PlayerView keeps the controller stable long enough to observe its
# semantic position without racing playback or the controller timeout.
sleep 0.5
dump video-viewer-paused; shot video-viewer-paused
assert_label qa-evidence/video-viewer-paused.xml 'Play'
video_progress_before_seek="$(video_progress_seconds qa-evidence/video-viewer-paused.xml)"
[ "$video_progress_before_seek" -gt 0 ] || { echo "video position did not advance past 0:00 before pause" >&2; exit 1; }
[ "$video_progress_before_seek" -lt 20 ] || { echo "video reached EOF before pause: $video_progress_before_seek" >&2; exit 1; }

# Exercise seek using the real Media3 progress node rather than a fixed screen coordinate.
# At 35% of a 20-second fixture the semantic position should settle near 7 seconds.
seek_video_progress_semantically qa-evidence/video-viewer-paused.xml
sleep 0.5
dump video-viewer-seeked; shot video-viewer-seeked
assert_label qa-evidence/video-viewer-seeked.xml 'Play'
video_progress_after_seek="$(video_progress_seconds qa-evidence/video-viewer-seeked.xml)"
[ "$video_progress_after_seek" -ne "$video_progress_before_seek" ] || {
  echo "Media3 seek did not change position: $video_progress_after_seek" >&2
  exit 1
}
if [ "$video_progress_after_seek" -lt 5 ] || [ "$video_progress_after_seek" -gt 9 ]; then
  echo "Media3 seek landed outside expected 35% target window: $video_progress_after_seek" >&2
  exit 1
fi
click_label qa-evidence/video-viewer-seeked.xml 'Edit'; sleep 4
dump video-editor; shot video-editor
for label in 'Trim video' 'Export' Undo Redo Reset 'Video trim timeline'; do assert_label qa-evidence/video-editor.xml "$label"; done
assert_label qa-evidence/video-editor.xml 'Start'
assert_label qa-evidence/video-editor.xml 'End'
# Drag the start trim handle to roughly one fifth of the timeline.
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('qa-evidence/video-editor.xml').getroot()
node=next((n for n in root.iter('node') if n.attrib.get('content-desc','') == 'Video trim timeline'),None)
if node is None: raise SystemExit('custom video trim timeline missing')
m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
if not m: raise SystemExit('video trim timeline has no bounds')
x1, y1, x2, y2=map(int,m.groups())
sx=x1 + max(2, round((x2-x1)*.01))
ex=x1 + round((x2-x1)*.22)
sy=(y1+y2)//2
subprocess.run(['adb','shell','input','swipe',str(sx),str(sy),str(ex),str(sy),'600'],check=True)
PY
sleep 1
dump video-editor-trimmed; shot video-editor-trimmed
assert_label qa-evidence/video-editor-trimmed.xml 'Export'

# Prove the marker-backed interrupted-export recovery on a real API 36 process.
# Start an export, wait until the pending marker exists, kill Later, relaunch the
# saved draft, reopen the video editor, and require the orphan marker/partial
# output to be removed before performing the successful export below.
click_label qa-evidence/video-editor-trimmed.xml 'Export'
pending_marker=''
for attempt in $(seq 1 20); do
  sleep 0.25
  pending_marker="$(adb shell run-as com.night.later find cache/video_edits -maxdepth 1 -type f -name '*.pending' 2>/dev/null | tr -d '\r' | head -n1)"
  [ -n "$pending_marker" ] && break
done
[ -n "$pending_marker" ] || {
  echo 'Video export never created its pending recovery marker' >&2
  exit 1
}
pending_output_name="$(adb shell run-as com.night.later cat "$pending_marker" | tr -d '\r\n')"
[ -n "$pending_output_name" ] || {
  echo 'Pending export marker did not identify its target output' >&2
  exit 1
}
adb shell am force-stop com.night.later
sleep 1

adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 3
dump video-recovery-home
assert_label qa-evidence/video-recovery-home.xml 'EditorBodyQA'
click_text qa-evidence/video-recovery-home.xml 'EditorBodyQA'
sleep 2
ensure_media_visible video-recovery-attached video
click_media_block qa-evidence/video-recovery-attached.xml video
sleep 2
dump video-recovery-viewer
assert_label qa-evidence/video-recovery-viewer.xml 'Edit'
click_label qa-evidence/video-recovery-viewer.xml 'Edit'
sleep 3
dump video-editor-recovered
assert_label qa-evidence/video-editor-recovered.xml 'Export'

for attempt in $(seq 1 20); do
  recovery_listing="$(adb shell run-as com.night.later find cache/video_edits -maxdepth 1 -type f -printf '%f\n' 2>/dev/null | tr -d '\r')"
  if ! printf '%s\n' "$recovery_listing" | grep -Fq '.pending' &&
     ! printf '%s\n' "$recovery_listing" | grep -Fxq "$pending_output_name"; then
    break
  fi
  sleep 0.25
done
recovery_listing="$(adb shell run-as com.night.later find cache/video_edits -maxdepth 1 -type f -printf '%f\n' 2>/dev/null | tr -d '\r')"
if printf '%s\n' "$recovery_listing" | grep -Fq '.pending' ||
   printf '%s\n' "$recovery_listing" | grep -Fxq "$pending_output_name"; then
  printf '%s\n' "$recovery_listing"
  echo 'Interrupted video export was not cleaned after process recreation' >&2
  exit 1
fi
shot video-editor-recovered

# Capture the entire export lifetime. If Transformer/codec work kills or ejects
# the Activity, the final hierarchy alone only shows Launcher and loses the cause.
adb logcat -c
adb logcat -v threadtime > qa-evidence/video-export-live-logcat.txt 2>&1 &
export_logcat_pid=$!
stop_export_logcat() {
  if [ -n "${export_logcat_pid:-}" ]; then
    kill "$export_logcat_pid" >/dev/null 2>&1 || true
    wait "$export_logcat_pid" >/dev/null 2>&1 || true
    export_logcat_pid=
  fi
}
capture_export_failure() {
  stop_export_logcat
  adb shell pidof com.night.later > qa-evidence/video-export-pid.txt 2>&1 || true
  adb shell dumpsys activity top > qa-evidence/video-export-activity-top.txt 2>&1 || true
  adb shell dumpsys activity processes > qa-evidence/video-export-processes.txt 2>&1 || true
  adb shell dumpsys meminfo com.night.later > qa-evidence/video-export-meminfo.txt 2>&1 || true
  adb logcat -b crash -d -v threadtime > qa-evidence/video-export-crash.txt 2>&1 || true
  adb shell run-as com.night.later find cache/video_edits -maxdepth 1 -type f -printf '%p %s bytes\\n' \
    > qa-evidence/video-export-private-files.txt 2>&1 || true
}
trap stop_export_logcat EXIT

click_label qa-evidence/video-editor-recovered.xml 'Export'
for attempt in $(seq 1 60); do
  sleep 1
  dump video-export-progress
  if grep -q 'Edited MP4 is ready' qa-evidence/video-export-progress.xml; then break; fi
  if ! grep -q 'package="com.night.later"' qa-evidence/video-export-progress.xml; then
    capture_export_failure
    cat qa-evidence/video-export-progress.xml
    echo 'Later left the foreground during video export; captured export diagnostics' >&2
    exit 1
  fi
  if grep -qi 'Video export failed\|Could not start video export\|did not create a playable file' qa-evidence/video-export-progress.xml; then
    capture_export_failure
    cat qa-evidence/video-export-progress.xml
    exit 1
  fi
done
if ! grep -q 'Edited MP4 is ready' qa-evidence/video-export-progress.xml; then
  capture_export_failure
  cat qa-evidence/video-export-progress.xml
  echo 'Video export did not complete inside the acceptance window; captured export diagnostics' >&2
  exit 1
fi
stop_export_logcat
assert_label qa-evidence/video-export-progress.xml 'Edited MP4 is ready'
shot video-export-complete
relpath="$(adb shell run-as com.night.later find cache/video_edits -type f -name '*.mp4' | tr -d '\r' | head -n1)"
[ -n "$relpath" ] || { echo 'No edited MP4 found in app-private cache' >&2; exit 1; }
adb exec-out run-as com.night.later cat "$relpath" > qa-evidence/edited-video-output.mp4
ffprobe -v error -show_entries format=format_name,duration -of default=noprint_wrappers=1 qa-evidence/edited-video-output.mp4 > qa-evidence/edited-video-probe.txt
grep -q 'format_name=.*mp4' qa-evidence/edited-video-probe.txt
python3 - <<'PY'
import re
s=open('qa-evidence/edited-video-probe.txt').read(); m=re.search(r'duration=([0-9.]+)',s)
if not m or float(m.group(1)) <= 0: raise SystemExit('exported video has no positive duration')
PY
click_label qa-evidence/video-export-progress.xml 'Update capsule'; sleep 4
ensure_media_visible video-export-attached video
video_edited_name="$(media_block_desc qa-evidence/video-export-attached.xml video)"
case "$video_edited_name" in
  *_edited.mp4) ;;
  *) echo "Edited video block was not version-labelled: $video_edited_name" >&2; exit 1 ;;
esac
click_media_block qa-evidence/video-export-attached.xml video; sleep 2
dump exported-video-viewer; shot exported-video-viewer
assert_label qa-evidence/exported-video-viewer.xml 'Exit fullscreen'
if grep -q 'content-desc="Play"' qa-evidence/exported-video-viewer.xml; then click_desc qa-evidence/exported-video-viewer.xml 'Play'; else adb shell input tap 180 350; fi
sleep 3
dump exported-video-playing; shot exported-video-playing
edited_video_progress="$(video_progress_seconds qa-evidence/exported-video-playing.xml)"
[ "$edited_video_progress" -gt 0 ] || { echo "edited video bytes did not play past zero" >&2; exit 1; }
click_label qa-evidence/exported-video-playing.xml 'Original'; sleep 1
dump exported-video-original; shot exported-video-original
assert_label qa-evidence/exported-video-original.xml 'Edited'

# Final mixed-content durability gate: persist the text + edited image + edited
# video document, kill the app process, reopen it, and prove both edited media
# outputs are still attached and playable/viewable.
adb shell input keyevent 4; sleep 2
dump mixed-before-restart
assert_desc qa-evidence/mixed-before-restart.xml 'Go back'
assert_label qa-evidence/mixed-before-restart.xml 'Media'
# Returning from the fullscreen viewer preserves the editor's prior scroll position.
# In a mixed document the body text may legitimately be above the visible image/video
# blocks, so prove the editor is restored first, then deliberately scroll back to the
# text rather than assuming the viewport starts at the top.
for attempt in 1 2 3 4 5 6; do
  if grep -q 'EditorBodyQA' qa-evidence/mixed-before-restart.xml; then break; fi
  adb shell input swipe 160 230 160 560 420
  sleep 0.6
  dump mixed-before-restart
done
assert_label qa-evidence/mixed-before-restart.xml 'EditorBodyQA'
for attempt in 1 2 3 4 5 6; do
  if grep -q 'text="Draft autosaved"' qa-evidence/mixed-before-restart.xml; then break; fi
  sleep 1
  dump mixed-before-restart
done
assert_label qa-evidence/mixed-before-restart.xml 'Draft autosaved'
shot mixed-before-restart
click_desc qa-evidence/mixed-before-restart.xml 'Go back'; sleep 2

adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 3
dump mixed-home-relaunch
shot mixed-home-relaunch
assert_label qa-evidence/mixed-home-relaunch.xml 'EditorBodyQA'
click_text qa-evidence/mixed-home-relaunch.xml 'EditorBodyQA'; sleep 3

ensure_media_visible mixed-image-reopened image
image_reopened_name="$(media_block_desc qa-evidence/mixed-image-reopened.xml image)"
case "$image_reopened_name" in
  *_edited.*) ;;
  *) echo "Edited image did not survive app restart: $image_reopened_name" >&2; exit 1 ;;
esac
click_media_block qa-evidence/mixed-image-reopened.xml image; sleep 2
dump mixed-image-viewer-reopened
shot mixed-image-viewer-reopened
assert_label qa-evidence/mixed-image-viewer-reopened.xml 'Close image'
assert_label qa-evidence/mixed-image-viewer-reopened.xml 'Original'
click_label qa-evidence/mixed-image-viewer-reopened.xml 'Close image'; sleep 1

ensure_media_visible mixed-video-reopened video
video_reopened_name="$(media_block_desc qa-evidence/mixed-video-reopened.xml video)"
case "$video_reopened_name" in
  *_edited.mp4) ;;
  *) echo "Edited video did not survive app restart: $video_reopened_name" >&2; exit 1 ;;
esac
click_media_block qa-evidence/mixed-video-reopened.xml video; sleep 2
dump mixed-video-viewer-reopened
shot mixed-video-viewer-reopened
assert_label qa-evidence/mixed-video-viewer-reopened.xml 'Exit fullscreen'
assert_label qa-evidence/mixed-video-viewer-reopened.xml 'Original'
if grep -q 'content-desc="Play"' qa-evidence/mixed-video-viewer-reopened.xml; then
  click_desc qa-evidence/mixed-video-viewer-reopened.xml 'Play'
else
  adb shell input tap 180 350
fi
sleep 3
dump mixed-video-playing-reopened
shot mixed-video-playing-reopened
mixed_reopened_progress="$(video_progress_seconds qa-evidence/mixed-video-playing-reopened.xml)"
[ "$mixed_reopened_progress" -gt 0 ] || {
  echo "reopened edited video did not play past zero" >&2
  exit 1
}

# Dark-mode visual smoke on the persisted mixed document. These screenshots are
# intentionally captured from real release surfaces for manual visual review.
adb shell input keyevent 4; sleep 1
adb shell cmd uimode night yes >/dev/null
adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 3
dump dark-home
shot dark-home
assert_label qa-evidence/dark-home.xml 'EditorBodyQA'
click_text qa-evidence/dark-home.xml 'EditorBodyQA'; sleep 3
dump dark-mixed-editor
shot dark-mixed-editor
assert_label qa-evidence/dark-mixed-editor.xml 'EditorBodyQA'

ensure_media_visible dark-image image
click_media_block qa-evidence/dark-image.xml image; sleep 2
dump dark-image-viewer
shot dark-image-viewer
assert_label qa-evidence/dark-image-viewer.xml 'Close image'
click_label qa-evidence/dark-image-viewer.xml 'Close image'; sleep 1

ensure_media_visible dark-video video
click_media_block qa-evidence/dark-video.xml video; sleep 2
dump dark-video-viewer
shot dark-video-viewer
assert_label qa-evidence/dark-video-viewer.xml 'Exit fullscreen'
adb shell input keyevent 4; sleep 1

adb shell cmd uimode night no >/dev/null
adb shell am force-stop com.night.later
adb shell am start -W -n com.night.later/.MainActivity >/dev/null
sleep 2

adb logcat -b crash -d > qa-evidence/media-crash.txt
if grep -q 'com.night.later' qa-evidence/media-crash.txt; then cat qa-evidence/media-crash.txt; exit 1; fi
echo LATER_MEDIA_QA_PASS
