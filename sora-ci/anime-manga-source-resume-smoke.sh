#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-anime-manga-source-resume
FULL_LIVE_MARKER="$OUT/FULL_LIVE_PASS"
mkdir -p "$OUT"
rm -f "$FULL_LIVE_MARKER"

adb uninstall com.night.sora >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/test-extension/build/outputs/apk/debug/test-extension-debug.apk"
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 2

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/sora-media-window.xml >/dev/null 2>&1 || true
}

# Hosted Android emulators can surface launcher/System UI ANR dialogs during
# cold boot even while Sora is healthy underneath. Dismiss only non-Sora
# system dialogs; a Sora ANR remains a hard test failure.
dismiss_emulator_system_dialogs() {
  dump_ui
  python3 <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET

path='/tmp/sora-media-window.xml'
try:
    root=ET.parse(path).getroot()
except Exception:
    raise SystemExit(0)

titles=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    low=text.lower()
    if "isn't responding" in low or 'is not responding' in low or 'keeps stopping' in low:
        titles.append(text)

# Android shows a one-time immersive/fullscreen tutorial the first time
# Sora's player hides system bars. It is an OS overlay, not app UI.
fullscreen_tutorial = any(
    (node.attrib.get('text') or '').strip() == 'Viewing full screen'
    for node in root.iter('node')
)
if fullscreen_tutorial:
    for node in root.iter('node'):
        text=(node.attrib.get('text') or '').strip()
        desc=(node.attrib.get('content-desc') or '').strip()
        if text != 'Got it' and desc != 'Got it':
            continue
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        print("Dismissing Android fullscreen tutorial via 'Got it'")
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        raise SystemExit(0)

if not titles:
    raise SystemExit(0)

title=titles[0]
if 'sora' in title.lower():
    print(f'Real Sora system error dialog detected: {title}', file=sys.stderr)
    raise SystemExit(2)

for label in ('Wait', 'Close app'):
    for node in root.iter('node'):
        text=(node.attrib.get('text') or '').strip()
        desc=(node.attrib.get('content-desc') or '').strip()
        if text != label and desc != label:
            continue
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        print(f"Dismissing emulator-only system dialog '{title}' via '{label}'")
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        raise SystemExit(0)

print(f'Emulator system dialog could not be dismissed: {title}', file=sys.stderr)
raise SystemExit(1)
PY
}

node_exists() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/sora-media-window.xml').getroot()
for node in root.iter('node'):
    if (node.attrib.get('text') or '').strip() == label or (node.attrib.get('content-desc') or '').strip() == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1" timeout="${2:-35}" elapsed=0
  while (( elapsed < timeout )); do
    if ! dismiss_emulator_system_dialogs; then
      echo "A real Sora/system dialog blocked '$label'" >&2
      shot "failure-system-dialog-${label//[^A-Za-z0-9]/_}"
      return 1
    fi
    if node_exists "$label"; then
      echo "Found '$label' after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo "Timed out waiting for '$label'" >&2
  shot "failure-${label//[^A-Za-z0-9]/_}"
  adb logcat -d -t 600 | grep -Ei 'com\.night\.sora|AndroidRuntime|FATAL EXCEPTION|AniList' | tail -n 180 >&2 || true
  return 1
}

# Returns 0 for a real catalog surface, 2 for the honest unavailable state,
# and 1 only when neither state appears. This lets a tab recover independently
# while AniList is degraded without turning real recovery into a false failure.
wait_for_catalog_state() {
  local timeout="${1:-50}" elapsed=0
  while (( elapsed < timeout )); do
    if ! dismiss_emulator_system_dialogs; then
      shot failure-system-dialog-catalog-state
      return 1
    fi
    if node_exists Details; then
      echo "Catalog recovered with real rows after ${elapsed}s"
      return 0
    fi
    if node_exists 'Catalog unavailable'; then
      echo "Catalog is truthfully unavailable after ${elapsed}s"
      return 2
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo 'Timed out waiting for either real catalog rows or the unavailable state' >&2
  shot failure-catalog-state
  return 1
}

tap_text() {
  local label="$1" timeout="${2:-12}" elapsed=0
  while (( elapsed < timeout )); do
    if ! dismiss_emulator_system_dialogs; then
      echo "A real Sora/system dialog blocked tap '$label'" >&2
      return 1
    fi
    dump_ui
    if python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try:
    root=ET.parse('/tmp/sora-media-window.xml').getroot()
except Exception:
    raise SystemExit(1)
points=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text != label and desc != label:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups())
        points.append(((y1,x1),(x1+x2)//2,(y1+y2)//2))
if not points:
    raise SystemExit(1)
_,x,y=sorted(points)[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
    then
      sleep 1
      return 0
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo "Timed out trying to tap '$label'" >&2
  shot "failure-tap-${label//[^A-Za-z0-9]/_}"
  return 1
}

input_query() {
  local hint="$1" query="$2"
  wait_for_node "$hint" 10
  tap_text "$hint"
  adb shell input text "$query"
  sleep 1
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

read_progress_json() {
  adb shell run-as com.night.sora cat shared_prefs/sora_core.xml > /tmp/current-sora-core.xml
  python3 - <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('/tmp/current-sora-core.xml').getroot()
raw=next((e.text for e in root.findall('string') if e.attrib.get('name')=='media_progress_v1'),None)
if raw is None:
    raise SystemExit('media_progress_v1 missing')
print(raw)
PY
}

assert_progress_identity() {
  local type="$1" min_position="$2" expected_item_suffix="$3" expected_source="$4"
  read_progress_json > /tmp/progress.json
  python3 - "$type" "$min_position" "$expected_item_suffix" "$expected_source" <<'PY'
import json,sys
type_name,minimum,suffix,source=sys.argv[1],int(sys.argv[2]),sys.argv[3],sys.argv[4]
items=json.load(open('/tmp/progress.json'))
entry=next((x for x in items if x.get('contentType')==type_name and x.get('title')=='Naruto'),None)
if entry is None:
    raise SystemExit(f'Naruto {type_name} progress missing: {items}')
print('progress',entry)
if int(entry.get('position',0)) < minimum:
    raise SystemExit(f'position too small: {entry.get("position")} < {minimum}')
if not str(entry.get('itemId','')).endswith(suffix):
    raise SystemExit(f'wrong child item: {entry.get("itemId")} expected suffix {suffix}')
if entry.get('resumeSourceId') != source:
    raise SystemExit(f'wrong resume source: {entry.get("resumeSourceId")} != {source}')
if entry.get('resumeExtensionPackage') != 'com.night.sora.ext.demo':
    raise SystemExit(f'wrong resume extension: {entry.get("resumeExtensionPackage")}')
if not str(entry.get('sourceId','')).startswith('anilist.'):
    raise SystemExit(f'catalog identity was not preserved: {entry.get("sourceId")}')
print(int(entry.get('position',0)))
PY
}

# Real AniList catalog -> CI-only external Anime source -> player -> exact resume.
wait_for_node Media 20
tap_text Media
wait_for_node 'Anime & Manga' 15
wait_for_node Details 45

tap_text 'Search Anime & Manga'
input_query 'Search Anime…' naruto
wait_for_node Naruto 30
tap_text Naruto
wait_for_node Episodes 30
wait_for_node 'Choose source' 20
shot 01-anime-no-source

tap_text 'Choose source'
wait_for_node 'Demo Anime' 15
tap_text 'Demo Anime'
wait_for_node 'Episode 1' 25
shot 02-anime-demo-source

tap_text 'Episode 1'
wait_for_node 'Demo Anime' 25
wait_for_node Pause 25
shot 03-anime-player
tap_text 'Forward 10 seconds'
sleep 4
assert_progress_identity ANIME 9000 '-e1' demo.anime > /tmp/anime-position.txt
ANIME_POSITION="$(tail -n 1 /tmp/anime-position.txt)"
echo "Saved Anime position: $ANIME_POSITION ms"

adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 2
wait_for_node 'Close search' 15
tap_text 'Close search'
wait_for_node 'Continue watching' 25
tap_text_below 'Continue watching' Naruto
wait_for_node 'Demo Anime' 25
wait_for_node Pause 25
sleep 2
assert_progress_identity ANIME "$ANIME_POSITION" '-e1' demo.anime >/dev/null
shot 04-anime-exact-resume

# Direct resume pushed only the player, so one back returns to Media root.
adb shell input keyevent KEYCODE_BACK
sleep 2
wait_for_node Manga 15
tap_text Manga

# Real AniList catalog -> CI-only external Manga source -> reader -> exact page resume.
tap_text 'Search Anime & Manga'
input_query 'Search Manga…' naruto
wait_for_node Naruto 30
tap_text Naruto
wait_for_node Chapters 30
wait_for_node 'Choose source' 20
shot 05-manga-no-source

tap_text 'Choose source'
wait_for_node 'Demo Manga' 15
tap_text 'Demo Manga'
wait_for_node 'Chapter 1' 25
shot 06-manga-demo-source

tap_text 'Chapter 1'
wait_for_node '1 / 42' 25