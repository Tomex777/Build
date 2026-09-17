#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-media-resume
mkdir -p "$OUT"

adb uninstall com.night.sora >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/test-extension/build/outputs/apk/debug/test-extension-debug.apk"
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"

# Let Android create the app data directory once, then seed Core using the same
# SharedPreferences JSON shape production Sora persists.
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 2
adb shell am force-stop com.night.sora
python3 <<'PY'
import json, time
from pathlib import Path
from xml.sax.saxutils import escape
now=int(time.time()*1000)
entries=[
  {
    "mediaId":"movie-1",
    "sourceId":"demo.video",
    "extensionPackage":"com.night.sora.ext.demo",
    "contentType":"MOVIE",
    "title":"The Last Platform",
    "itemId":"movie-1",
    "itemLabel":"The Last Platform",
    "position":5000,
    "total":30000,
    "resumeSourceId":"demo.video",
    "resumeExtensionPackage":"com.night.sora.ext.demo",
    "subtitle":"2h 06m · Thriller",
    "artworkUrl":None,
    "updatedAt":now,
  },
  {
    "mediaId":"manga-1",
    "sourceId":"demo.manga",
    "extensionPackage":"com.night.sora.ext.demo",
    "contentType":"MANGA",
    "title":"After Rain",
    "itemId":"manga-1-c3",
    "itemLabel":"Chapter 3",
    "position":7,
    "total":42,
    "resumeSourceId":"demo.manga",
    "resumeExtensionPackage":"com.night.sora.ext.demo",
    "subtitle":"42 chapters · Ongoing",
    "artworkUrl":None,
    "updatedAt":now-1,
  },
]
raw=json.dumps(entries,separators=(',',':'))
xml="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n<string name=\"media_progress_v1\">"+escape(raw)+"</string>\n</map>\n"
Path('/tmp/sora_core.xml').write_text(xml)
PY
adb push /tmp/sora_core.xml /data/local/tmp/sora_core.xml >/dev/null
adb shell run-as com.night.sora mkdir -p shared_prefs
adb shell run-as com.night.sora cp /data/local/tmp/sora_core.xml shared_prefs/sora_core.xml
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 5

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/sora-resume-window.xml >/dev/null 2>&1 || true
}

node_exists() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/sora-resume-window.xml').getroot()
for node in root.iter('node'):
    if (node.attrib.get('text') or '').strip() == label or (node.attrib.get('content-desc') or '').strip() == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1" timeout="${2:-20}" elapsed=0
  while (( elapsed < timeout )); do
    if node_exists "$label"; then
      echo "Found '$label' after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo "Timed out waiting for '$label'" >&2
  shot "failure-${label//[^A-Za-z0-9]/_}"
  adb logcat -d -t 400 | grep -Ei 'com\.night\.sora|AndroidRuntime|FATAL EXCEPTION' | tail -n 120 >&2 || true
  return 1
}

tap_text() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/sora-resume-window.xml').getroot()
points=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text != label and desc != label:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); points.append(((y1,x1),(x1+x2)//2,(y1+y2)//2))
if not points: raise SystemExit(f'UI node not found: {label}')
_,x,y=sorted(points)[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

tap_text_below() {
  local heading="$1" label="$2"
  dump_ui
  python3 - "$heading" "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
heading,label=sys.argv[1:]
root=ET.parse('/tmp/sora-resume-window.xml').getroot()
def bounds(node):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    return tuple(map(int,m.groups())) if m else None
heads=[]
for n in root.iter('node'):
    if (n.attrib.get('text') or '').strip()==heading:
        b=bounds(n)
        if b: heads.append(b)
if not heads: raise SystemExit(f'Heading not found: {heading}')
cut=min(b[3] for b in heads)
choices=[]
for n in root.iter('node'):
    if (n.attrib.get('text') or '').strip()!=label: continue
    b=bounds(n)
    if b and b[1] >= cut: choices.append(b)
if not choices: raise SystemExit(f'No {label} below {heading}')
b=sorted(choices,key=lambda v:(v[1],v[0]))[0]
subprocess.check_call(['adb','shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2)])
PY
  sleep 2
}

assert_progress() {
  local media_id="$1" min_position="$2"
  adb shell run-as com.night.sora cat shared_prefs/sora_core.xml > /tmp/current-sora-core.xml
  python3 - "$media_id" "$min_position" <<'PY'
import json, sys, xml.etree.ElementTree as ET
media_id=sys.argv[1]; minimum=int(sys.argv[2])
root=ET.parse('/tmp/current-sora-core.xml').getroot()
raw=next((e.text for e in root.findall('string') if e.attrib.get('name')=='media_progress_v1'),None)
if raw is None: raise SystemExit('media_progress_v1 missing')
items=json.loads(raw)
entry=next((x for x in items if x.get('mediaId')==media_id),None)
if entry is None: raise SystemExit(f'progress missing for {media_id}')
print('progress',media_id,entry)
if int(entry.get('position',0)) < minimum: raise SystemExit(f'position regressed for {media_id}: {entry.get("position")} < {minimum}')
if not entry.get('resumeSourceId') or not entry.get('resumeExtensionPackage'): raise SystemExit('resume identity missing')
PY
}

# Movie: Continue must directly resolve demo.video and preserve a 5s resume point.
tap_text Media
wait_for_node 'Anime & Manga' 10
tap_text 'Anime & Manga'
wait_for_node 'Movies & TV' 10
tap_text 'Movies & TV'
wait_for_node 'Continue watching' 15
shot 01-movie-continue

tap_text_below 'Continue watching' 'The Last Platform'
wait_for_node Pause 20
wait_for_node 'Demo Movies & TV' 10
shot 02-movie-resumed
sleep 3
assert_progress 'movie-1' 5000

# Direct resume pushed only the player, so one back returns to the Media root.
adb shell input keyevent KEYCODE_BACK
sleep 2
wait_for_node 'Movies & TV' 10
tap_text 'Movies & TV'
wait_for_node 'Anime & Manga' 10
tap_text 'Anime & Manga'
wait_for_node Manga 10
tap_text Manga
wait_for_node 'Continue reading' 15
shot 03-manga-continue

tap_text_below 'Continue reading' 'After Rain'
wait_for_node '7 / 42' 20
wait_for_node 'Demo Manga' 10
shot 04-manga-resumed-page-7

tap_text 'Next page'
tap_text 'Next page'
wait_for_node '9 / 42' 15
sleep 1
assert_progress 'manga-1' 9
shot 05-manga-progress-page-9

echo 'Sora direct media resume smoke passed.'
