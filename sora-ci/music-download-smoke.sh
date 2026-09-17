#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-music-download-smoke
mkdir -p "$OUT"
SERVER_PID=""

cleanup() {
  if [ -n "$SERVER_PID" ]; then kill "$SERVER_PID" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

curl -fsSL --retry 3 --max-time 30 'https://storage.googleapis.com/exoplayer-test-media-0/play.mp3' -o /tmp/sora-proof.mp3

python3 -u - <<'PY' > "$OUT/http.log" 2>&1 &
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
DATA=Path('/tmp/sora-proof.mp3').read_bytes()
class H(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): print(fmt % args, flush=True)
    def do_GET(self):
        print('GET', self.path, 'proof=', self.headers.get('X-Sora-Download-Proof'), flush=True)
        if self.path != '/proof.mp3':
            self.send_response(404); self.end_headers(); return
        if self.headers.get('X-Sora-Download-Proof') != 'allowed':
            self.send_response(403); self.end_headers(); return
        self.send_response(200)
        self.send_header('Content-Type','audio/mpeg')
        self.send_header('Content-Length',str(len(DATA)))
        self.end_headers(); self.wfile.write(DATA)
ThreadingHTTPServer(('0.0.0.0',8765),H).serve_forever()
PY
SERVER_PID=$!
sleep 1
curl -fsS -H 'X-Sora-Download-Proof: allowed' http://127.0.0.1:8765/proof.mp3 -o /tmp/proof-check.mp3
cmp /tmp/sora-proof.mp3 /tmp/proof-check.mp3

adb uninstall com.night.sora >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/test-extension/build/outputs/apk/debug/test-extension-debug.apk"
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
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
  adb pull /sdcard/window.xml /tmp/sora-download-window.xml >/dev/null 2>&1 || true
}

node_exists() {
  local label="$1"; dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try: root=ET.parse('/tmp/sora-download-window.xml').getroot()
except Exception: raise SystemExit(1)
for n in root.iter('node'):
    if (n.attrib.get('text') or '').strip()==label or (n.attrib.get('content-desc') or '').strip()==label: raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_node() {
  local label="$1" timeout="${2:-30}" elapsed=0
  while (( elapsed < timeout )); do
    if node_exists "$label"; then echo "Found $label"; return 0; fi
    sleep 1; elapsed=$((elapsed+1))
  done
  echo "Timed out: $label" >&2
  shot "failure-${label//[^A-Za-z0-9]/_}"
  adb logcat -d -t 600 | grep -Ei 'Sora|MusicDownload|ExoPlayer|AndroidRuntime|FATAL' | tail -n 180 >&2 || true
  return 1
}

tap_text() {
  local label="$1"; dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]; root=ET.parse('/tmp/sora-download-window.xml').getroot(); pts=[]
for n in root.iter('node'):
    text=(n.attrib.get('text') or '').strip(); desc=(n.attrib.get('content-desc') or '').strip()
    if text != label and desc != label: continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); pts.append(((x1+x2)//2,(y1+y2)//2))
if not pts: raise SystemExit('not found: '+label)
x,y=pts[0]; subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

tap_media_tab() {
  if node_exists Media; then tap_text Media; return; fi
  read -r w h <<<"$(adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)"
  adb shell input tap $((w*3/10)) $((h*94/100)); sleep 2
}

open_music() {
  tap_media_tab
  wait_node 'Anime & Manga' 15
  tap_text 'Anime & Manga'
  wait_node Music 15
  tap_text Music
  wait_node 'Offline Proof' 25
}

shot 00-home
open_music
shot 01-music-home

tap_text 'Offline Proof'
wait_node 'Mini player' 20
tap_text 'Mini player'
wait_node 'Offline Proof' 15
wait_node Download 15
shot 02-now-playing

tap_text Download
wait_node Downloaded 45
shot 03-downloaded

grep -q 'proof= allowed' "$OUT/http.log"
adb shell 'find /sdcard/Android/data/com.night.sora/files/Music/downloads -type f -size +0c 2>/dev/null' > "$OUT/files-before-offline.txt" || true
grep -q . "$OUT/files-before-offline.txt"

# Kill the only server capable of serving this proof track, restart Sora process,
# and play again. Passing now requires Core to resolve the completed local file.
kill "$SERVER_PID"; SERVER_PID=""
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 4
open_music
tap_text 'Offline Proof'
wait_node 'Mini player' 20
tap_text 'Mini player'
wait_node Downloaded 15
wait_node Pause 20
# MusicPlaybackController sets this exact stream label only after selecting the verified local file.
wait_node Offline 15
shot 04-offline-local-playback

# Remove through the real overflow action; this avoids confusing the Downloaded source label
# with the Downloaded secondary action.
tap_text 'Track options'
wait_node 'Remove download' 15
tap_text 'Remove download'
wait_node Download 20
sleep 2
adb shell 'find /sdcard/Android/data/com.night.sora/files/Music/downloads -type f -size +0c 2>/dev/null' > "$OUT/files-after-delete.txt" || true
if [ -s "$OUT/files-after-delete.txt" ]; then
  echo 'Music download file still exists after delete:' >&2
  cat "$OUT/files-after-delete.txt" >&2
  exit 1
fi
shot 05-removed

echo 'Sora Music download/offline smoke passed.'
