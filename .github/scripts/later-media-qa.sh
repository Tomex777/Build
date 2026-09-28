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
  -t 8 -c:v libx264 -profile:v baseline -preset ultrafast -pix_fmt yuv420p \
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
    if timeout 12s adb shell uiautomator dump --compressed /sdcard/later-window.xml >/dev/null 2>&1 \\
      && adb shell test -s /sdcard/later-window.xml; then ok=1; break; fi
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
# Start playback and prove its position advances beyond zero before pausing.
if grep -q 'content-desc="Play"' qa-evidence/video-viewer.xml; then click_desc qa-evidence/video-viewer.xml 'Play'; else adb shell input tap 180 350; fi
sleep 3
dump video-viewer-playing; shot video-viewer-playing
python3 - qa-evidence/video-viewer-playing.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); times=[]
for n in root.iter('node'):
    text=n.attrib.get('text','').strip()
    m=re.fullmatch(r'(?:(\d+):)?(\d{1,2}):(\d{2})',text)
    if m:
        h=int(m.group(1) or 0); times.append(h*3600+int(m.group(2))*60+int(m.group(3)))
if len(set(times)) < 2 or not any(0 < t < max(times) for t in times):
    raise SystemExit(f'video position did not advance past 0:00: {times}')
PY
if grep -q 'content-desc="Pause"' qa-evidence/video-viewer-playing.xml; then click_desc qa-evidence/video-viewer-playing.xml 'Pause'; fi
sleep 1
click_label qa-evidence/video-viewer-playing.xml 'Edit'; sleep 4
dump video-editor; shot video-editor
for label in 'Trim video' 'Export MP4' Undo Redo Reset; do assert_label qa-evidence/video-editor.xml "$label"; done
assert_label qa-evidence/video-editor.xml 'Start'
assert_label qa-evidence/video-editor.xml 'End'
# Drag the start trim handle to roughly one fifth of the timeline.
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('qa-evidence/video-editor.xml').getroot()
node=next((n for n in root.iter('node') if n.attrib.get('text','').startswith('Start  ')),None)
if node is None: raise SystemExit('start time label missing')
m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
if not m: raise SystemExit('start time label has no bounds')
_, y1, _, _=map(int,m.groups())
size=subprocess.check_output(['adb','shell','wm','size'],text=True)
sm=re.search(r'(\d+)x(\d+)',size)
if not sm: raise SystemExit('screen size unavailable')
w=int(sm.group(1)); density=subprocess.check_output(['adb','shell','wm','density'],text=True)
dm=re.search(r'Physical density: (\d+)',density); scale=int(dm.group(1))/160 if dm else 1
left=round(24*scale); right=w-left
sy=max(0,y1-round(24*scale)); ex=left+round((right-left)*.22)
subprocess.run(['adb','shell','input','swipe',str(left),str(sy),str(ex),str(sy),'600'],check=True)
PY
sleep 1
dump video-editor-trimmed; shot video-editor-trimmed
assert_label qa-evidence/video-editor-trimmed.xml 'Export MP4'
click_label qa-evidence/video-editor-trimmed.xml 'Export MP4'
for attempt in $(seq 1 60); do
  sleep 1
  dump video-export-progress
  if grep -q 'Edited MP4 is ready' qa-evidence/video-export-progress.xml; then break; fi
  if grep -qi 'Video export failed\|Could not start video export\|did not create a playable file' qa-evidence/video-export-progress.xml; then cat qa-evidence/video-export-progress.xml; exit 1; fi
done
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
python3 - qa-evidence/exported-video-playing.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); times=[]
for n in root.iter('node'):
    m=re.fullmatch(r'(?:(\d+):)?(\d{1,2}):(\d{2})',n.attrib.get('text','').strip())
    if m: times.append(int(m.group(1) or 0)*3600+int(m.group(2))*60+int(m.group(3)))
if len(set(times)) < 2 or not any(0 < t < max(times) for t in times): raise SystemExit(f'edited video bytes did not play past zero: {times}')
PY
click_label qa-evidence/exported-video-playing.xml 'Original'; sleep 1
dump exported-video-original; shot exported-video-original
assert_label qa-evidence/exported-video-original.xml 'Edited'

adb logcat -b crash -d > qa-evidence/media-crash.txt
if grep -q 'com.night.later' qa-evidence/media-crash.txt; then cat qa-evidence/media-crash.txt; exit 1; fi
echo LATER_MEDIA_QA_PASS
