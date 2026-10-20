#!/usr/bin/env bash
set -euo pipefail

# Fresh-user smoke verifies empty-dependent Cover quiz and Meme caption.
# The second stage seeds two saved anime and one meme into the *same*
# SharedPreferences format that CoreRepository.persistLibrary writes.
OUT=/tmp/sora-arcade-saved-media
mkdir -p "$OUT"
adb shell am force-stop com.night.sora
python3 - <<'PY'
import json, xml.etree.ElementTree as ET
seed = [
    ("anime-paper","Paper Moons","ANIME","https://picsum.photos/seed/sora-paper-moons/600/900"),
    ("anime-night","Night Signal","ANIME","https://picsum.photos/seed/sora-night-signal/600/900"),
    ("meme-one","One Saved Meme","MEME","https://picsum.photos/seed/sora-one-meme/700/700"),
]
items=[]
for key,title,kind,art in seed:
    items.append(dict(
        id=f"media:com.night.sora.ext.demo:demo.{kind.lower()}:{key}",
        label=title,kind=kind.title(),detail="",mediaId=key,
        sourceId=f"demo.{kind.lower()}",extensionPackage="com.night.sora.ext.demo",
        contentType=kind,mediaSubtitle="",artworkUrl=art,
    ))
root=ET.Element("map")
ET.SubElement(root,"string",name="library_v1").text=json.dumps(items)
ET.ElementTree(root).write("/tmp/sora-arcade-seed.xml",encoding="utf-8",xml_declaration=True)
PY
# run-as does not guarantee a current working directory inside package data.
# Use the absolute per-app credential-encrypted data path for all emulator APIs.
PREFS=/data/data/com.night.sora/shared_prefs
adb shell run-as com.night.sora mkdir -p "$PREFS"
adb shell run-as com.night.sora dd "of=$PREFS/sora_core.xml" < /tmp/sora-arcade-seed.xml
adb shell run-as com.night.sora cat "$PREFS/sora_core.xml" | grep -q library_v1
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 4

dump_ui() {
  adb shell uiautomator dump /sdcard/sora-saved-arcade.xml >/dev/null 2>&1 || true
  adb pull /sdcard/sora-saved-arcade.xml /tmp/sora-saved-arcade.xml >/dev/null 2>&1 || true
}
node_exists() {
  dump_ui
  python3 - "$1" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse("/tmp/sora-saved-arcade.xml").getroot()
label=sys.argv[1]
for n in root.iter("node"):
    if (n.attrib.get("text","").strip()==label or
        n.attrib.get("content-desc","").strip()==label):
        raise SystemExit(0)
raise SystemExit(1)
PY
}
tap_once() {
  dump_ui
  python3 - "$1" <<'PY'
import sys,re,subprocess,xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse("/tmp/sora-saved-arcade.xml").getroot()
exact=[];partial=[]
for n in root.iter("node"):
    a=n.attrib
    text=a.get("text","").strip()
    desc=a.get("content-desc","").strip()
    bounds=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
    if not bounds: continue
    x1,y1,x2,y2=map(int,bounds.groups())
    candidate=((x1+x2)//2,(y1+y2)//2)
    if text==label or desc==label: exact.append(candidate)
    elif label in text or label in desc: partial.append(candidate)
points=exact or partial
if not points: raise SystemExit(1)
x,y=points[0]
subprocess.check_call(["adb","shell","input","tap",str(x),str(y)])
PY
}
wait_node() {
  local label="$1" timeout="${2:-22}"
  for i in $(seq 1 "$timeout"); do
    if node_exists "$label"; then return 0; fi
    sleep 1
  done
  echo "Timed out: $label" >&2
  adb exec-out screencap -p > "$OUT/failure-${label//[^a-zA-Z0-9]/_}.png"
  return 1
}
tap() { tap_once "$1"; sleep 1; }
tap_scrolling() {
  local label="$1"
  local dimensions width height
  dimensions=$(adb shell wm size | sed -n 's/.*size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
  read -r width height <<< "$dimensions"
  for i in $(seq 1 10); do
    if node_exists "$label"; then tap "$label"; return 0; fi
    adb shell input swipe "$((width/2))" "$((height*75/100))" "$((width/2))" "$((height*30/100))" 320
    sleep 1
  done
  echo "Missing saved-media game card: $label" >&2
  return 1
}
shot() {
  adb exec-out screencap -p > "$OUT/$1.png"
  dump_ui
  cp /tmp/sora-saved-arcade.xml "$OUT/$1.xml"
}

tap Games
tap_scrolling "Cover quiz"
wait_node "Play 2 rounds"
tap "Play 2 rounds"
wait_node "ROUND 1 OF 2"
wait_node "Choose the title"
shot cover-quiz-real-round
tap "Paper Moons"
wait_node Next
tap Next
wait_node "ROUND 2 OF 2"
tap "Paper Moons"
wait_node "Finish quiz"
tap "Finish quiz"
wait_node "Quiz complete"
adb shell run-as com.night.sora cat shared_prefs/sora_games.xml | grep -q 'name="quizzes" value="1"'
shot cover-quiz-persisted-score
tap "All games"

tap_scrolling "Meme caption"
wait_node "Write your caption…"
shot meme-caption-real-editor
if node_exists "Next meme"; then
  echo "A single saved meme must not show a useless Next meme control" >&2
  exit 1
fi
tap "Write your caption…"
adb shell input text 'Sora%sWorks'
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_scrolling "Save caption"
adb shell run-as com.night.sora cat shared_prefs/sora_arcade_captions_v1.xml | grep -q 'Sora Works'
shot meme-caption-saved

# Search must react on every edit, rather than caching the initial Library
# contents when only the category filter changes.
tap "Library"
wait_node "Paper Moons"
wait_node "Night Signal"
tap "Search library"
wait_node "Search saved items"
tap "Search saved items"
adb shell input text 'Night'
sleep 1
wait_node "Night Signal"
if node_exists "Paper Moons" || node_exists "One Saved Meme"; then
  shot library-search-leaked-items
  echo 'Library search did not hide unrelated saved titles.' >&2
  exit 1
fi
shot library-live-search

echo 'Cover quiz: real two-round choices and persisted stats verified.'
echo 'Meme caption: editable custom input, saved caption and single-meme UI verified.'
