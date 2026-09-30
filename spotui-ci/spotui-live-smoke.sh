#!/usr/bin/env bash
# Lyra acceptance revision: resilient transport + offline persistence proof
set -euo pipefail

OUT=/tmp/spotui-artifacts
mkdir -p "$OUT"
LIVE_LOGCAT_PID=""

capture_resolver_logs() {
  if [[ -f "$OUT/resolver-live-logcat.txt" ]]; then
    grep -Ei 'SpotuiYouTubeMusic|LyraPlayback|LyraAudioRange|LYRA_PLAYBACK_PROOF|LYRA_DOWNLOAD_PROOF|transport failure|range (open|complete|failed|short)|player start|player result|resolved|signature timestamp|potoken|newpipe|PlaybackException|ExoPlayer|HttpDataSource|googlevideo' "$OUT/resolver-live-logcat.txt" \
      | tail -n 260 > "$OUT/resolver-summary.txt" || true
  fi
}

cleanup() {
  if [[ -n "${LIVE_LOGCAT_PID:-}" ]]; then
    kill "$LIVE_LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LIVE_LOGCAT_PID" >/dev/null 2>&1 || true
  fi
  capture_resolver_logs
  adb shell pm enable com.android.launcher3 >/dev/null 2>&1 || true
}
trap cleanup EXIT

APK="$SPOTUI_ROOT/spotui-app/build/outputs/apk/debug/spotui-app-debug.apk"
SOURCE_APK="$SPOTUI_ROOT/spotui-youtube-music-extension/build/outputs/apk/debug/spotui-youtube-music-extension-debug.apk"
SOURCE_TEST_APK="$(find "$SPOTUI_ROOT/spotui-youtube-music-extension/build/outputs/apk/androidTest/debug" -type f -name '*.apk' | head -n 1)"
APP_TEST_APK="$(find "$SPOTUI_ROOT/spotui-app/build/outputs/apk/androidTest/debug" -type f -name '*.apk' | head -n 1)"

# First prove the real Lyra source adapter against the pinned shared engine. This is separate from
# the challenge-aware catalog/UI smoke below: a green host proof requires verified audio, refresh
# of the same stable representation and an actual resumed CDN 206 range.
adb uninstall com.night.spotui.ext.youtube.music.test >/dev/null 2>&1 || true
adb uninstall com.night.spotui.ext.youtube.music >/dev/null 2>&1 || true
test -f "$SOURCE_TEST_APK"
adb install -r "$SOURCE_APK"
adb install -r "$SOURCE_TEST_APK"
adb logcat -c || true
HOST_TEST_OUTPUT=""
HOST_TEST_OK=0
for attempt in 1 2 3; do
  echo "LYRA_HOST_PROOF attempt=$attempt"
  set +e
  HOST_TEST_OUTPUT="$(timeout 75s adb shell am instrument -w -r \
    -e class com.night.sora.youtubemusic.SharedYouTubeEngineHostTest \
    com.night.spotui.ext.youtube.music.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
  HOST_TEST_RC=$?
  set -e
  printf '%s\n' "$HOST_TEST_OUTPUT" | tee "$OUT/shared-engine-host-instrumentation-attempt-$attempt.txt"
  if [[ "$HOST_TEST_RC" -eq 0 ]] && grep -Eq '^OK \(1 test\)' <<<"$HOST_TEST_OUTPUT"; then
    HOST_TEST_OK=1
    cp "$OUT/shared-engine-host-instrumentation-attempt-$attempt.txt" "$OUT/shared-engine-host-instrumentation.txt"
    break
  fi
  if grep -Eqi 'Unable to resolve host|No address associated with hostname|UnknownHost|NetworkFailure|Bootstrap I/O' <<<"$HOST_TEST_OUTPUT"; then
    echo "Transient emulator DNS/network failure; recovering before retry." >&2
    adb shell settings put global private_dns_mode off >/dev/null 2>&1 || true
    adb shell svc wifi disable >/dev/null 2>&1 || true
    sleep 2
    adb shell svc wifi enable >/dev/null 2>&1 || true
    adb reconnect >/dev/null 2>&1 || true
    sleep 5
    continue
  fi
  break
done
if [[ "$HOST_TEST_OK" -ne 1 ]]; then
  echo "Lyra shared-engine host instrumentation failed after retry policy." >&2
  adb logcat -d -v threadtime | tail -n 400 > "$OUT/shared-engine-host-failure-logcat.txt" || true
  exit 1
fi
adb logcat -d -v brief | grep 'LYRA_ENGINE_HOST_PROOF' \
  | tee "$OUT/shared-engine-host-proof.txt" || true
if [[ ! -s "$OUT/shared-engine-host-proof.txt" ]]; then
  echo "Lyra host test passed JUnit but emitted no real transport proof." >&2
  exit 1
fi
adb uninstall com.night.spotui.ext.youtube.music.test >/dev/null 2>&1 || true

# Prove Lyra's actual downloader before any catalog/UI smoke. The first instrumentation
# resolves through the installed YouTube Music source, downloads the entire representation,
# pins it, and reads every byte through a cache-only data source. Then remove the source
# extension entirely and run a second instrumentation pass that must read the same pinned
# bytes without any possible network/source fallback.
adb uninstall com.night.spotui.test >/dev/null 2>&1 || true
adb uninstall com.night.spotui >/dev/null 2>&1 || true
test -f "$APP_TEST_APK"
adb install -r "$APK"
adb install -r "$APP_TEST_APK"
adb logcat -c || true
DOWNLOAD_TEST_OUTPUT="$(adb shell am instrument -w -r \
  -e class com.night.spotui.playback.LyraAudioDownloadTest#downloadsEntireAudioAndPinsIt \
  com.night.spotui.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
printf '%s\n' "$DOWNLOAD_TEST_OUTPUT" | tee "$OUT/full-audio-download-instrumentation.txt"
if ! grep -Eq '^OK \(1 test\)' <<<"$DOWNLOAD_TEST_OUTPUT"; then
  echo "Lyra full audio download instrumentation failed." >&2
  adb logcat -d -v threadtime | tail -n 500 > "$OUT/full-audio-download-failure-logcat.txt" || true
  exit 1
fi
adb logcat -d -v brief | grep 'LYRA_FULL_DOWNLOAD_PROOF' \
  | tee "$OUT/full-audio-download-proof.txt" || true
if [[ ! -s "$OUT/full-audio-download-proof.txt" ]]; then
  echo "Lyra full download passed JUnit but emitted no completion proof." >&2
  exit 1
fi

echo "LYRA_RESTART_PHASE cleaning instrumentation process before real app restart"
adb shell am force-stop com.night.spotui >/dev/null 2>&1 || true
adb shell am force-stop com.night.spotui.test >/dev/null 2>&1 || true
adb uninstall com.night.spotui.test >/dev/null 2>&1 || true
for _ in $(seq 1 20); do
  APP_PID="$(adb shell pidof com.night.spotui 2>/dev/null | tr -d '\r' || true)"
  TEST_PID="$(adb shell pidof com.night.spotui.test 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$APP_PID" && -z "$TEST_PID" ]]; then break; fi
  sleep 1
done
adb shell ps -A | grep -E 'com\.night\.spotui($|:|\.test)' \
  | tee "$OUT/processes-before-real-restart.txt" || true
adb shell run-as com.night.spotui ls -la files/lyra-audio-cache \
  | tee "$OUT/download-cache-after-force-stop.txt"
touch "$OUT/FULL_AUDIO_DOWNLOAD_AND_CACHE_REOPEN_PASS"


# Leave the system launcher enabled; disabling it can wedge accessibility/UIAutomator on API 36.
adb shell am force-stop com.night.spotui
set +e
APP_START_OUTPUT="$(timeout 25s adb shell am start -W -n com.night.spotui/.MainActivity 2>&1)"
APP_START_RC=$?
set -e
printf '%s\n' "$APP_START_OUTPUT" | tee "$OUT/app-restart-am-start.txt"
if [[ "$APP_START_RC" -eq 124 ]]; then
  echo "Lyra activity start command itself timed out after instrumentation cleanup." >&2
fi
sleep 7

dump_ui() {
  rm -f /tmp/spotui.xml
  adb shell rm -f /sdcard/spotui.xml >/dev/null 2>&1 || true
  if timeout 6s adb shell uiautomator dump /sdcard/spotui.xml >/dev/null 2>&1 && \
     timeout 4s adb pull /sdcard/spotui.xml /tmp/spotui.xml >/dev/null 2>&1 && \
     [[ -s /tmp/spotui.xml ]]; then
    return 0
  fi
  adb shell am force-stop com.android.uiautomator >/dev/null 2>&1 || true
  return 1
}

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  dump_ui
  cp /tmp/spotui.xml "$OUT/$name.xml" || true
}

node_exists() {
  local label="$1"
  dump_ui || return 1
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/spotui.xml').getroot()
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text == label or desc == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

node_contains() {
  local label="$1"
  dump_ui || return 1
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
needle=sys.argv[1].lower()
root=ET.parse('/tmp/spotui.xml').getroot()
for node in root.iter('node'):
    value=((node.attrib.get('text') or '')+' '+(node.attrib.get('content-desc') or '')).lower()
    if needle in value:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    if node_exists "$label"; then return 0; fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  shot failure-node
  echo "Timed out waiting for UI node: $label" >&2
  cat /tmp/spotui.xml >&2 || true
  if [[ "$label" == "Home" ]]; then
    APP_PID="$(adb shell pidof com.night.spotui 2>/dev/null | tr -d '\r' || true)"
    adb logcat -d -v threadtime | grep -E 'LYRA_STARTUP|LyraStartup|SpotPlayback|SimpleCache|SQLite|ExoPlayer|AndroidRuntime|ANR' \
      > "$OUT/lyra-startup-logcat-before-dump.txt" || true
    if [[ -n "$APP_PID" ]]; then
      adb logcat -c || true
      adb shell kill -3 "$APP_PID" >/dev/null 2>&1 || true
      sleep 3
      adb logcat -d -v threadtime > "$OUT/lyra-startup-thread-dump.txt" || true
    fi
    adb shell dumpsys activity top > "$OUT/lyra-startup-activity.txt" 2>&1 || true
  fi
  capture_resolver_logs
  echo "--- SpotUI resolver summary ---" >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  echo "--- end resolver summary ---" >&2
  adb logcat -d -t 800 | grep -Ei 'com\.night\.spotui|youtube|innertube|ExoPlayer|AndroidRuntime|FATAL|Exception' | tail -n 200 >&2 || true
  return 1
}

wait_for_contains() {
  local label="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    if node_contains "$label"; then return 0; fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  shot failure-contains
  adb logcat -d -t 1000 | grep -Ei 'com\.night\.spotui|youtube|innertube|ExoPlayer|AndroidRuntime|FATAL|Exception' | tail -n 240 >&2 || true
  return 1
}

tap_text() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/spotui.xml').getroot()
matches=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text != label and desc != label: continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    matches.append(((x1+x2)//2,(y1+y2)//2))
if not matches: raise SystemExit("No UI node for "+label)
x,y=matches[-1]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

wait_and_tap_node() {
  local label="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    if dump_ui; then
      if python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/spotui.xml').getroot()
matches=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text != label and desc != label:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    matches.append(((x1+x2)//2,(y1+y2)//2))
if not matches:
    raise SystemExit(1)
x,y=matches[-1]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
print(f'Tapped transient UI node {label} at x={x} y={y}')
PY
      then
        sleep 1
        return 0
      fi
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  shot failure-node
  echo "Timed out waiting to tap UI node: $label" >&2
  return 1
}

scroll_until_node() {
  local label="$1"
  local attempts="${2:-10}"
  for _ in $(seq 1 "$attempts"); do
    if node_exists "$label"; then return 0; fi
    python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
scrollables=[]
for node in root.iter('node'):
    if node.attrib.get('scrollable') != 'true': continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    scrollables.append(((x2-x1)*(y2-y1),x1,y1,x2,y2))
if not scrollables: raise SystemExit('No scrollable catalog page found')
_,x1,y1,x2,y2=max(scrollables)
x=(x1+x2)//2
subprocess.check_call(['adb','shell','input','swipe',str(x),str(y1+(y2-y1)*4//5),str(x),str(y1+(y2-y1)//3),'450'])
PY
    sleep 1
  done
  return 1
}

scroll_catalog_once() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
scrollables=[]
for node in root.iter('node'):
    if node.attrib.get('scrollable') != 'true': continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    scrollables.append(((x2-x1)*(y2-y1),x1,y1,x2,y2))
if not scrollables: raise SystemExit('No scrollable catalog page found')
_,x1,y1,x2,y2=max(scrollables)
x=(x1+x2)//2
subprocess.check_call([
    'adb','shell','input','swipe',
    str(x),str(y1+(y2-y1)*4//5),
    str(x),str(y1+(y2-y1)*2//5),
    '400'
])
PY
  sleep 1
}


scroll_player_down() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET

root=ET.parse('/tmp/spotui.xml').getroot()
candidates=[]
for node in root.iter('node'):
    if node.attrib.get('scrollable') != 'true':
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    candidates.append(((x2-x1)*(y2-y1),x1,y1,x2,y2))

if candidates:
    _,x1,y1,x2,y2=max(candidates)
    x=(x1+x2)//2
    start_y=y1+(y2-y1)*4//5
    end_y=y1+(y2-y1)//4
else:
    # Compose's Now Playing verticalScroll is not always exported through
    # UIAutomator. Swipe inside the visible player sheet, away from the
    # system navigation area, so the parent scroll container can intercept it.
    x=540
    start_y=2150
    end_y=1050

subprocess.check_call([
    'adb','shell','input','swipe',
    str(x),str(start_y),
    str(x),str(end_y),
    '450'
])
print(f'Scrolled player down x={x} startY={start_y} endY={end_y}')
PY
  sleep 1
}

accessible_action_visible() {
  local label="$1"
  local min_height="${2:-1}"
  dump_ui || return 1
  python3 - "$label" "$min_height" <<'PY'
import re, sys, xml.etree.ElementTree as ET

label=sys.argv[1]
min_height=int(sys.argv[2])
root=ET.parse('/tmp/spotui.xml').getroot()
parents={child: parent for parent in root.iter() for child in parent}

def bounds(node):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m:
        return None
    x1,y1,x2,y2=map(int,m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return [x1,y1,x2,y2]

def clipped_bounds(node):
    b=bounds(node)
    if b is None:
        return None
    cur=parents.get(node)
    while cur is not None:
        pb=bounds(cur)
        if pb is not None:
            b=[max(b[0],pb[0]),max(b[1],pb[1]),min(b[2],pb[2]),min(b[3],pb[3])]
            if b[2] <= b[0] or b[3] <= b[1]:
                return None
        cur=parents.get(cur)
    return b

for node in root.iter('node'):
    if (node.attrib.get('content-desc') or '').strip() != label:
        continue
    target=node
    b=bounds(target)
    while target is not None and (
        b is None or target.attrib.get('clickable') != 'true'
    ):
        target=parents.get(target)
        if target is not None:
            b=bounds(target)
    if target is None or b is None:
        continue
    visible=clipped_bounds(target)
    if visible is None:
        continue
    x1,y1,x2,y2=visible
    if x2-x1 >= 48 and y2-y1 >= min_height:
        print(f'Visible accessibility action {label}: [{x1},{y1}][{x2},{y2}]')
        raise SystemExit(0)

raise SystemExit(1)
PY
}

tap_first_discography_release() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
nodes=list(root.iter('node'))
parents={child: parent for parent in root.iter() for child in parent}
start=next((i for i,n in enumerate(nodes) if (n.attrib.get('text') or '').strip() == 'Discography'),None)
if start is None:
    raise SystemExit('Discography section not visible')
disc=nodes[start]

def bounds(node):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    return tuple(map(int,m.groups())) if m else None

def is_descendant(node, ancestor):
    cur=node
    while cur is not None:
        if cur is ancestor:
            return True
        cur=parents.get(cur)
    return False

artist_page=disc
while artist_page is not None and artist_page.attrib.get('scrollable') != 'true':
    artist_page=parents.get(artist_page)
if artist_page is None:
    raise SystemExit('Artist page scroll container not found')

disc_bounds=bounds(disc)
disc_y=disc_bounds[1] if disc_bounds else 0

# The actual album cards live inside a nested horizontal scroll container below
# the Discography heading. Their clickable parent is intentionally unlabeled;
# the visible album title/cover text is exposed by descendants (for example
# "30", "25", "21"). Find that nested rail and tap its first real card.
rails=[]
for node in nodes[start+1:]:
    if not is_descendant(node, artist_page):
        continue
    if node.attrib.get('scrollable') != 'true':
        continue
    b=bounds(node)
    if not b or b[1] < disc_y:
        continue
    rails.append(node)

for rail in rails:
    for node in rail.iter('node'):
        if node is rail or node.attrib.get('clickable') != 'true':
            continue
        b=bounds(node)
        if not b:
            continue
        x1,y1,x2,y2=b
        if x2-x1 < 120 or y2-y1 < 80:
            continue
        labels=[]
        for child in node.iter('node'):
            t=(child.attrib.get('text') or '').strip()
            d=(child.attrib.get('content-desc') or '').strip()
            if t: labels.append(t)
            if d: labels.append(d)
        label=next((v for v in labels if v and v not in {'Like','Downloaded'}), 'release')
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        print('Tapped discography release:',label)
        raise SystemExit(0)

raise SystemExit('No visible album card found in Discography rail')
PY
  sleep 2
}

tap_accessible_action() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/spotui.xml').getroot()
parents={child: parent for parent in root.iter() for child in parent}

def bounds(node):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if not m:
        return None
    x1,y1,x2,y2=map(int,m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return [x1,y1,x2,y2]

def clipped_bounds(node):
    b=bounds(node)
    if b is None:
        return None
    cur=parents.get(node)
    while cur is not None:
        pb=bounds(cur)
        if pb is not None:
            b=[max(b[0],pb[0]),max(b[1],pb[1]),min(b[2],pb[2]),min(b[3],pb[3])]
            if b[2] <= b[0] or b[3] <= b[1]:
                return None
        cur=parents.get(cur)
    return b

matches=[]
for node in root.iter('node'):
    desc=(node.attrib.get('content-desc') or '').strip()
    if desc != label:
        continue
    target=node
    b=bounds(target)
    # Compose merged semantics can put the label on a zero-sized child while
    # the clickable parent owns the real touch target.
    while target is not None and (
        b is None or target.attrib.get('clickable') != 'true'
    ):
        target=parents.get(target)
        if target is not None:
            b=bounds(target)
    if target is None or b is None:
        continue
    visible=clipped_bounds(target)
    if visible is None:
        continue
    x1,y1,x2,y2=visible
    if x2 <= x1 or y2 <= y1:
        continue
    matches.append(((x2-x1)*(y2-y1),(x1+x2)//2,(y1+y2)//2,visible))

if not matches:
    raise SystemExit('No visible tappable accessibility node for '+label)
_,x,y,visible=max(matches)
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
print(f'Tapped accessibility action {label} at x={x} y={y} visible={visible}')
PY
  sleep 2
}


tap_seek_fraction() {
  local fraction="$1"
  dump_ui
  python3 - "$fraction" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET

fraction=float(sys.argv[1])
if not (0.0 <= fraction <= 1.0):
    raise SystemExit('Seek fraction must be between 0 and 1')

root=ET.parse('/tmp/spotui.xml').getroot()
parents={child: parent for parent in root.iter() for child in parent}

def bounds(node):
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m:
        return None
    x1,y1,x2,y2=map(int,m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return x1,y1,x2,y2

candidates=[]
for node in root.iter('node'):
    desc=(node.attrib.get('content-desc') or '').strip()
    if desc != 'Seek bar':
        continue
    target=node
    b=bounds(target)
    while b is None and target is not None:
        target=parents.get(target)
        if target is not None:
            b=bounds(target)
    if b is not None:
        x1,y1,x2,y2=b
        candidates.append(((x2-x1)*(y2-y1),x1,y1,x2,y2))

if not candidates:
    raise SystemExit('Seek bar accessibility bounds not found')

_,x1,y1,x2,y2=max(candidates)
x=round(x1 + (x2-x1) * fraction)
# Stay just inside the gesture target at the extreme ends.
x=max(x1+1, min(x2-1, x))
y=(y1+y2)//2
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
print(f'Tapped Seek bar at fraction={fraction:.3f} x={x} y={y} bounds=[{x1},{y1}][{x2},{y2}]')
PY
  sleep 2
}

tap_search_field() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
candidates=[]
for node in root.iter('node'):
    cls=node.attrib.get('class','')
    text=(node.attrib.get('text') or '').strip()
    if cls != 'android.widget.EditText' and text != 'What do you want to listen to?':
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    candidates.append(((x1+x2)//2,(y1+y2)//2))
if not candidates: raise SystemExit('Search field not found')
x,y=candidates[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 1
}

replace_search_text() {
  local value="$1"
  tap_search_field
  # Move to the end and clear enough characters for every smoke query.
  adb shell input keyevent KEYCODE_MOVE_END >/dev/null 2>&1 || true
  for _ in $(seq 1 48); do
    adb shell input keyevent KEYCODE_DEL >/dev/null 2>&1 || true
  done
  adb shell input text "$value"
  adb shell input keyevent KEYCODE_ENTER
}

playback_proof_count() {
  grep -c 'LYRA_PLAYBACK_PROOF' "$OUT/resolver-live-logcat.txt" 2>/dev/null || true
}

download_proof_count() {
  grep -c 'LYRA_DOWNLOAD_PROOF' "$OUT/resolver-live-logcat.txt" 2>/dev/null || true
}

wait_for_new_download_proof() {
  local before="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    local after
    after="$(download_proof_count)"
    if (( after > before )); then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  return 1
}

wait_for_new_playback_proof() {
  local before="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    local after
    after="$(playback_proof_count)"
    if (( after > before )); then
      return 0
    fi
    if node_contains 'Sign in to continue' >/dev/null 2>&1; then
      return 2
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  return 1
}

assert_no_raw_timeout() {
  dump_ui
  if grep -Eqi 'Timed out awaiting|30000 ms|TimeoutCancellationException' /tmp/spotui.xml; then
    shot failure-raw-timeout
    echo "Raw timeout text leaked into SpotUI UI." >&2
    return 1
  fi
}

tap_first_adele_result() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
parents={child: parent for parent in root.iter() for child in parent}
for node in root.iter('node'):
    value=((node.attrib.get('text') or '')+' '+(node.attrib.get('content-desc') or '')).lower()
    if 'adele' not in value: continue
    if node.attrib.get('class') == 'android.widget.EditText': continue
    m0=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if m0 and int(m0.group(2)) < 500: continue
    current=node
    while current is not None:
        if current.attrib.get('clickable') == 'true':
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', current.attrib.get('bounds',''))
            if m:
                x1,y1,x2,y2=map(int,m.groups())
                subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
                raise SystemExit(0)
        current=parents.get(current)
raise SystemExit('No clickable Adele result found')
PY
  sleep 2
}

# Assert the listening-space home screen and persistent navigation using
# visible app semantics rather than the legacy application label.
wait_for_node Home 20
wait_for_node 'Your listening space' 20
wait_for_node Search 20
wait_for_node Library 20
shot 00-home

# Product-level offline proof: the download must survive the instrumentation process,
# a force-stop/restart, and source unavailability. Play it from Lyra's real Library UI.
tap_text Library
wait_for_node 'YOUR MUSIC' 15
wait_for_node 'Downloads' 15
wait_for_node 'Play Never Gonna Give You Up' 15
shot 00a-download-survives-restart

adb shell am force-stop com.night.spotui.ext.youtube.music >/dev/null 2>&1 || true
adb shell pm disable-user --user 0 com.night.spotui.ext.youtube.music \
  | tee "$OUT/source-disabled-for-real-offline-playback.txt"
adb logcat -c || true
tap_accessible_action 'Play Never Gonna Give You Up'
wait_for_node 'Mini player' 15

OFFLINE_PLAYBACK_OK=0
for _ in $(seq 1 45); do
  if adb logcat -d -v brief | grep -q 'LYRA_PLAYBACK_PROOF.*host=lyra-cache.invalid'; then
    OFFLINE_PLAYBACK_OK=1
    break
  fi
  sleep 1
done
adb logcat -d -v threadtime | grep -E 'LyraPlayback|LYRA_PLAYBACK_PROOF|LyraAudioRange|transport failure' \
  | tail -n 260 > "$OUT/offline-playback-logcat.txt" || true
if [[ "$OFFLINE_PLAYBACK_OK" -ne 1 ]]; then
  shot failure-offline-playback
  adb shell pm enable com.night.spotui.ext.youtube.music >/dev/null 2>&1 || true
  echo "Downloaded track did not produce real playback proof with the source extension disabled." >&2
  cat "$OUT/offline-playback-logcat.txt" >&2 2>/dev/null || true
  exit 1
fi
shot 00b-offline-playing
touch "$OUT/OFFLINE_PLAYBACK_AFTER_RESTART_PASS"
adb shell pm enable com.night.spotui.ext.youtube.music >/dev/null
echo "Lyra downloaded track survived restart and played from cache with the source extension disabled."

if [[ "${LYRA_MINIMUM_SDK_MODE:-0}" == "1" ]]; then
  shot 00c-api26-download-offline-pass
  touch "$OUT/MINIMUM_SDK_26_DOWNLOAD_OFFLINE_PASS"
  echo "Lyra API 26 minimum-SDK acceptance passed full download, restart persistence and offline playback."
  exit 0
fi

if [[ "${LYRA_CORE_ACCEPTANCE_MODE:-0}" == "1" ]]; then
  # Keep a live log stream for seek and re-download proof counters.
  adb logcat -c || true
  adb logcat -v threadtime > "$OUT/resolver-live-logcat.txt" 2>&1 &
  LIVE_LOGCAT_PID=$!

  # Player controls: pause, resume, seek.
  tap_text 'Mini player'
  wait_for_node 'NOW PLAYING' 15
  wait_for_node 'Pause' 10
  tap_text 'Pause'
  wait_for_node 'Play' 10
  shot 00c-paused
  tap_text 'Play'
  wait_for_node 'Pause' 10

  tap_seek_fraction 0.70
  SEEK_OK=0
  for _ in $(seq 1 20); do
    if grep -q 'LYRA_SEEK_PROOF' "$OUT/resolver-live-logcat.txt" 2>/dev/null; then
      SEEK_OK=1
      break
    fi
    sleep 1
  done
  if [[ "$SEEK_OK" -ne 1 ]]; then
    shot failure-seek
    echo "Lyra seekbar did not produce a real player seek proof." >&2
    exit 1
  fi
  grep 'LYRA_SEEK_PROOF' "$OUT/resolver-live-logcat.txt" | tail -n 1 > "$OUT/seek-proof.txt"

  # Queue lives below the fold on the phone-sized Now Playing surface.
  # Scroll until it is visible, then open it and verify the active download.
  if ! accessible_action_visible 'Queue' 72; then
    for _ in 1 2 3 4; do
      scroll_player_down || true
      if accessible_action_visible 'Queue' 72; then break; fi
    done
  fi
  if ! accessible_action_visible 'Queue' 40; then
    shot failure-queue-visible
    echo "Lyra Queue card never became sufficiently visible to tap." >&2
    exit 1
  fi
  tap_accessible_action 'Queue'
  wait_for_node 'Queue Never Gonna Give You Up' 10
  shot 00d-queue
  tap_accessible_action 'Queue Never Gonna Give You Up'
  wait_for_node 'NOW PLAYING' 10

  # Background playback must keep the service, MediaSession and media notification alive.
  adb shell input keyevent KEYCODE_HOME
  sleep 3
  adb shell dumpsys activity services com.night.spotui | tee "$OUT/background-services.txt" | grep -q 'SpotPlaybackService'
  adb shell dumpsys media_session | tee "$OUT/background-media-session.txt" | grep -q 'com.night.spotui'
  adb shell dumpsys notification --noredact > "$OUT/background-notifications.txt" 2>&1 || true
  if ! grep -q 'com.night.spotui' "$OUT/background-notifications.txt"; then
    echo "Lyra media notification was not present while playback was active in background." >&2
    exit 1
  fi
  adb shell am start -W -n com.night.spotui/.MainActivity >/dev/null
  sleep 3
  if ! node_exists 'NOW PLAYING'; then
    wait_for_node 'Mini player' 10
    tap_text 'Mini player'
    wait_for_node 'NOW PLAYING' 10
  fi
  touch "$OUT/BACKGROUND_MEDIA_CONTROLS_PASS"

  # Delete the completed download, deliberately slow the emulator network so
  # cancellation is observable, cancel it, restore full speed, then download
  # the complete track again.
  wait_for_node 'Remove download' 10
  tap_text 'Remove download'
  wait_for_node 'Download Never Gonna Give You Up' 10
  touch "$OUT/DOWNLOAD_DELETE_PASS"

  adb emu network speed gsm >/dev/null 2>&1 || true
  tap_text 'Download Never Gonna Give You Up'
  if ! wait_and_tap_node 'Cancel download' 8; then
    adb emu network speed full >/dev/null 2>&1 || true
    shot failure-download-cancel-window
    echo "Lyra download completed before the cancellation control could be exercised." >&2
    exit 1
  fi
  adb emu network speed full >/dev/null 2>&1 || true
  wait_for_node 'Download Never Gonna Give You Up' 12
  touch "$OUT/DOWNLOAD_CANCEL_PASS"

  DOWNLOAD_PROOF_BEFORE="$(download_proof_count)"
  tap_text 'Download Never Gonna Give You Up'
  if ! wait_for_new_download_proof "$DOWNLOAD_PROOF_BEFORE" 120; then
    shot failure-redownload
    echo "Lyra did not complete a fresh download after deletion/cancellation." >&2
    exit 1
  fi
  wait_for_node 'Remove download' 15
  shot 00e-redownloaded
  touch "$OUT/DOWNLOAD_DELETE_CANCEL_RETRY_PASS"

  touch "$OUT/API36_CORE_DOWNLOAD_OFFLINE_PASS"
  touch "$OUT/PLAYER_INTERACTION_ACCEPTANCE_PASS"
  echo "Lyra API 36 interaction acceptance passed pause/resume, seek, queue, background media controls, delete, cancel and re-download."
  exit 0
fi

tap_text Search
wait_for_node Search 15

# Regression proof: explicit searches must remain reusable. Autocomplete used to
# consume source workers, which made the first search succeed and later searches
# hang until the raw 30 second IPC timeout surfaced.
replace_search_text Adele
wait_for_contains Adele 35
assert_no_raw_timeout
shot 01-search-adele

replace_search_text Coldplay
wait_for_contains Coldplay 35
assert_no_raw_timeout
shot 02-search-coldplay

replace_search_text Adele
wait_for_node 'Play Easy On Me' 35
assert_no_raw_timeout
shot 03-search-adele-again

# Dismiss the IME before tapping catalog controls below the search field.
adb shell input keyevent KEYCODE_BACK || true
sleep 1

# Follow Adele's stable artist ID from the actual search result, then open the
# first album card from the artist's discography. This proves catalog navigation
# independently from playback resolution.
tap_accessible_action 'Open artist Adele'
wait_for_node Artist 20
scroll_until_node Discography 10 || {
  shot failure-artist-discography
  echo "Artist browse omitted its discography section." >&2
  exit 1
}
shot 04-artist
ALBUM_OPENED=0
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  if tap_first_discography_release; then
    ALBUM_OPENED=1
    break
  fi
  scroll_catalog_once
done
if [[ "$ALBUM_OPENED" -ne 1 ]]; then
  shot failure-artist-album-card
  echo "Discography was visible but no album card became tappable after scrolling." >&2
  exit 1
fi
wait_for_node Tracks 35
wait_for_contains songs 10
assert_album_track_metadata() {
  dump_ui
  python3 <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
nodes=list(root.iter('node'))
start=next((i for i,n in enumerate(nodes) if (n.attrib.get('text') or '').strip() == 'Tracks'),None)
if start is None: raise SystemExit('Album tracks section was not visible')
labels=[(n.attrib.get('text') or '').strip() for n in nodes[start+1:]]
artists=sum(label == 'Adele' for label in labels)
provider_fallback=sum(label == 'YouTube Music' for label in labels)
if artists < 1 or provider_fallback:
    raise SystemExit('Album track metadata is wrong: Adele rows=%d, provider fallback rows=%d' % (artists,provider_fallback))
PY
}
# Capture the actual album metadata surface before checking its contents so
# failed assertions retain actionable screenshot and UI-tree evidence.
shot 05-album
assert_album_track_metadata
touch "$OUT/CATALOG_ARTIST_ALBUM_PASS"
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell input keyevent BACK || true
sleep 2
tap_text Library
wait_for_node 'YOUR MUSIC' 15
shot 06-library
tap_text Search
wait_for_node Search 12
wait_for_node 'Play Easy On Me' 12
sleep 1
adb logcat -c || true
adb logcat -v threadtime > "$OUT/resolver-live-logcat.txt" 2>&1 &
LIVE_LOGCAT_PID=$!
PROOF_BEFORE="$(playback_proof_count)"
tap_text 'Play Easy On Me'
wait_for_node 'Mini player' 15

set +e
wait_for_new_playback_proof "$PROOF_BEFORE" 60
PLAYBACK_RESULT=$?
set -e

if [[ "$PLAYBACK_RESULT" -eq 2 ]]; then
  shot 08-youtube-challenge
  tap_text 'Sign in to continue · Open'
  wait_for_node 'Close sign-in' 20
  shot 09-sign-in-flow
  capture_resolver_logs
  if grep -q 'LYRA_PLAYBACK_PROOF' "$OUT/resolver-live-logcat.txt" || \
      grep -q 'resolved id=' "$OUT/resolver-live-logcat.txt" || \
      grep -Eq 'LyraAudioRange: range open host=' "$OUT/resolver-live-logcat.txt"; then
    echo "Challenge classification rejected: a stream or media range appeared without playback proof." >&2
    exit 1
  fi
  cat > "$OUT/AUDIO_TRANSPORT_INCONCLUSIVE.txt" <<'EOF'
status=INCONCLUSIVE
reason=YouTube challenged the anonymous playback session before returning an audio URL
playback_proof=NOT_OBTAINED
resolved_media_url=NO
cdn_audio_range=NOT_OPENED
download=NOT_ATTEMPTED
EOF
  touch "$OUT/SOURCE_BROWSER_SESSION_FALLBACK_SHOWN"
  echo "::warning::YouTube challenged the emulator before resolving audio. Playback and download are inconclusive, not passed; see AUDIO_TRANSPORT_INCONCLUSIVE.txt and resolver-summary.txt."
  echo "App and catalog smoke passed; audio transport remains unproven because YouTube challenged the anonymous emulator session."
  exit 0
elif [[ "$PLAYBACK_RESULT" -ne 0 ]]; then
  capture_resolver_logs
  echo "Easy On Me never produced READY + duration + advancing position + cached audio bytes." >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  exit 1
fi

shot 08-playing-proof
adb shell dumpsys activity services com.night.spotui | grep -q 'SpotPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.spotui'

# Open the player and verify the production-facing layout no longer exposes
# source/codec diagnostics. The lyrics entry should be the primary card.
tap_text 'Mini player'
wait_for_node 'NOW PLAYING' 12
wait_for_node 'Lyrics' 12
if node_exists 'probe'; then
  shot failure-garbage-lyrics
  echo "Malformed one-word lyrics payload leaked into the production player." >&2
  exit 1
fi
if node_exists 'SOURCE'; then
  shot failure-source-card
  echo "Legacy SOURCE diagnostic card is still visible." >&2
  exit 1
fi
shot 09-now-playing

# Regression proof for stale queue selection.
wait_for_node 'Now playing Easy On Me' 8
tap_text 'Next'
sleep 2
if node_exists 'Now playing Easy On Me'; then
  shot failure-stale-next-track
  echo "Next kept the old Now Playing title." >&2
  exit 1
fi
wait_for_contains 'Now playing' 8
shot 10-next-transition
tap_text 'Close player'
sleep 1

# Real-device regression tracks: these previously resolved metadata correctly
# while their final audio transport failed on the Galaxy A16.
replace_search_text Fast
wait_for_contains 'Juice WRLD' 35
wait_for_node 'Play Fast' 35
FAST_PROOF_BEFORE="$(playback_proof_count)"
tap_text 'Play Fast'
wait_for_node 'Mini player' 15
if ! wait_for_new_playback_proof "$FAST_PROOF_BEFORE" 60; then
  capture_resolver_logs
  shot failure-fast-playback
  echo "Juice WRLD - Fast did not produce actual playback proof." >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  exit 1
fi
shot 11-fast-playing

# Exercise the downloader on the same regression track after streaming has
# already populated part of the shared cache. Success proves the downloader can
# reuse cached spans and fetch the missing ranges without a second architecture.
tap_text 'Mini player'
wait_for_node 'Now playing Fast' 12
DOWNLOAD_PROOF_BEFORE="$(download_proof_count)"
tap_text 'Download Fast'
if ! wait_for_new_download_proof "$DOWNLOAD_PROOF_BEFORE" 120; then
  capture_resolver_logs
  shot failure-fast-download
  echo "Juice WRLD - Fast did not complete an offline download." >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  exit 1
fi
wait_for_node 'Remove download' 15
shot 12-fast-downloaded
tap_text 'Close player'
sleep 1

replace_search_text 'Wishing%sWell'
wait_for_contains 'Juice WRLD' 35
wait_for_node 'Play Wishing Well' 35
WISHING_PROOF_BEFORE="$(playback_proof_count)"
tap_text 'Play Wishing Well'
wait_for_node 'Mini player' 15
if ! wait_for_new_playback_proof "$WISHING_PROOF_BEFORE" 60; then
  capture_resolver_logs
  shot failure-wishing-well-playback
  echo "Juice WRLD - Wishing Well did not produce actual playback proof." >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  exit 1
fi
shot 13-wishing-well-playing

# The playback proof marker is emitted only after READY, known duration,
# advancing position, and non-zero cached audio bytes.
grep -q 'LYRA_PLAYBACK_PROOF' "$OUT/resolver-live-logcat.txt"
touch "$OUT/FULL_ANONYMOUS_PLAYBACK_PASS"

adb shell am start -W -a android.settings.SETTINGS >/dev/null
sleep 3
adb shell dumpsys activity services com.night.spotui | grep -q 'SpotPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.spotui'
shot 14-background

echo "Lyra core + YouTube Music source proved real cached-byte playback for Easy On Me, Fast, and Wishing Well."

echo "SpotUI core + extension smoke passed."
