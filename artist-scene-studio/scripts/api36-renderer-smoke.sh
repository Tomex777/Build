#!/usr/bin/env bash
set -euo pipefail

# The workflow uses this marker to distinguish emulator-runner startup failures from
# failures that happened after Mise's real smoke test began.
touch .runtime-smoke-started

APP_ID=studio.artistscene.app
ACTIVITY="$APP_ID/.MainActivity"
APK=app/build/outputs/apk/debug/app-debug.apk
PROJECT_FILE=files/projects/feasibility-stage.scene.json
API_LEVEL="${API_LEVEL:-36}"
API_TAG="api${API_LEVEL}"

LOGCAT="artist-scene-studio-${API_TAG}-logcat.txt"
XML=artist-scene-studio-window.xml
PNG="artist-scene-studio-${API_TAG}.png"
TEST_LOG=artist-scene-studio-connected-test.log
STARTUP_PNG="artist-scene-studio-${API_TAG}-startup.png"
SELECTED_PNG="artist-scene-studio-${API_TAG}-selected-object.png"
PROJECT_BROWSER_PNG="artist-scene-studio-${API_TAG}-project-browser.png"
ADD_PNG="artist-scene-studio-${API_TAG}-add-sheet.png"
ASSET_LIBRARY_PNG="artist-scene-studio-${API_TAG}-asset-library.png"
TRANSFORM_PNG="artist-scene-studio-${API_TAG}-transform.png"
ROTATE_PNG="artist-scene-studio-${API_TAG}-rotate-gizmo.png"
SCALE_PNG="artist-scene-studio-${API_TAG}-scale-gizmo.png"
MORE_TOOLS_PNG="artist-scene-studio-${API_TAG}-more-tools.png"
HIERARCHY_PNG="artist-scene-studio-${API_TAG}-hierarchy.png"
INSPECTOR_PNG="artist-scene-studio-${API_TAG}-inspector.png"
POSE_PNG="artist-scene-studio-${API_TAG}-pose-tools.png"
POSED_PNG="artist-scene-studio-${API_TAG}-character-posed.png"
REFERENCE_PNG="artist-scene-studio-${API_TAG}-reference.png"
SAVED_PNG="artist-scene-studio-${API_TAG}-saved.png"
RESTORED_PNG="artist-scene-studio-${API_TAG}-restored.png"
IMPORTED_ASSET_PNG="artist-scene-studio-${API_TAG}-imported-asset.png"
FAILURE_PNG="artist-scene-studio-${API_TAG}-failure.png"
SAVED_JSON=artist-scene-studio-saved-scene.json
IMPORT_FIXTURE_NAME="ActStudioImportCube.glb"
IMPORT_FIXTURE_DEVICE="/sdcard/Download/${IMPORT_FIXTURE_NAME}"

frame_selected_character() {
  dump_window_once || fail "Could not inspect camera tool for character framing"
  local camera_coords
  camera_coords="$(tag_coords "camera-tools")" || fail "Camera tool was not exposed"
  tap_coords "Camera framing" "$camera_coords"
  sleep 1
  dump_window_once || fail "Could not inspect frame-selected control"
  capture_screen "artist-scene-studio-${API_TAG}-camera-controls.png" || fail "Could not capture camera controls"
  local frame_coords
  frame_coords="$(find_tag_by_scrolling "frame-selected" 6)" || fail "Frame selected was not exposed"
  tap_coords "Frame selected character" "$frame_coords"
  sleep 1
  dump_window_once || fail "Could not inspect framed character"
}

hierarchy_actor_coords() {
  local tag="actor-$1"
  if ! find_visible_tag_by_scrolling_back "$tag" 8; then
    find_visible_tag_by_scrolling "$tag" 8 || return 1
  fi
  dump_window_once || return 1
  tag_coords "$tag"
}

adb_bounded() {
  timeout 20s adb "$@"
}

stop_logcat_capture() {
  if [ -n "${LOGCAT_PID:-}" ]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi
  timeout 10s adb logcat -d -v threadtime > "$LOGCAT" 2>&1 || true
}

refresh_logcat() {
  local snapshot="${LOGCAT}.next"
  if timeout 10s adb logcat -d -v threadtime > "$snapshot" 2>&1; then
    mv "$snapshot" "$LOGCAT"
  else
    rm -f "$snapshot"
  fi
}

device_reachable() {
  timeout 8s adb get-state 2>/dev/null | grep -qx "device"
}

process_alive() {
  timeout 8s adb shell pidof "$APP_ID" >/dev/null 2>&1
}

require_process_alive() {
  local description="$1"
  local reachable=false
  for _ in 1 2 3; do
    if device_reachable; then
      reachable=true
      break
    fi
    sleep 2
  done
  [ "$reachable" = true ] || fail "Android API $API_LEVEL emulator/ADB became unreachable while waiting for: $description"

  set +e
  timeout 8s adb shell pidof "$APP_ID" >/dev/null 2>&1
  local status=$?
  set -e
  case "$status" in
    0) return 0 ;;
    124) fail "Android API $API_LEVEL guest shell stopped responding while waiting for: $description" ;;
    *) fail "Mise process exited while waiting for: $description" ;;
  esac
}

capture_screen() {
  local output="$1"
  local remote="/sdcard/$(basename "$output")"
  rm -f "$output"
  for attempt in 1 2 3; do
    if adb_bounded shell screencap -p "$remote" >/dev/null 2>&1 \
      && adb_bounded pull "$remote" "$output" >/dev/null 2>&1 \
      && test -s "$output"; then
      echo "Captured screenshot: $output"
      return 0
    fi
    sleep 1
  done
  echo "Could not capture a non-empty screen image: $output" >&2
  return 1
}

dump_window_once() {
  local remote="/sdcard/artist-scene-studio-window.xml"
  rm -f "$XML"
  for attempt in 1 2 3; do
    timeout 10s adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if timeout 20s adb shell uiautomator dump "$remote" >/tmp/mise-uiautomator.txt 2>&1 \
      && timeout 20s adb pull "$remote" "$XML" >/dev/null 2>&1 \
      && test -s "$XML"; then
      return 0
    fi
    rm -f "$XML"
    echo "UiAutomator hierarchy capture attempt $attempt failed; retrying" >&2
    sleep 1
  done
  cat /tmp/mise-uiautomator.txt >&2 || true
  return 1
}

diagnostics() {
  set +e
  echo "=== Android API $API_LEVEL renderer diagnostics ===" | tee -a "$TEST_LOG"
  echo "=== host emulator state ===" | tee -a "$TEST_LOG"
  ps -eo pid,ppid,stat,%cpu,%mem,rss,vsz,cmd | grep -E "[e]mulator.*-port 5554|[q]emu-system" | tee -a "$TEST_LOG"
  free -h | tee -a "$TEST_LOG"
  ls -lah /tmp/android-runner 2>&1 | tee -a "$TEST_LOG"
  (dmesg 2>/dev/null | tail -n 80 || true) | tee -a "$TEST_LOG"
  adb_bounded devices -l | tee -a "$TEST_LOG"

  # Capture process-death evidence first. SurfaceFlinger/screencap diagnostics can
  # become unavailable seconds later if the emulator graphics process is also failing.
  echo "=== ApplicationExitInfo ===" | tee -a "$TEST_LOG"
  timeout 8s adb shell dumpsys activity exit-info "$APP_ID" 2>&1 | tail -n 160 | tee -a "$TEST_LOG"
  echo "=== crash log buffer ===" | tee -a "$TEST_LOG"
  timeout 8s adb logcat -b crash -d -v threadtime 2>&1 | tail -n 240 | tee -a "$TEST_LOG"
  echo "=== native crash DropBox ===" | tee -a "$TEST_LOG"
  timeout 8s adb shell dumpsys dropbox --print data_app_native_crash 2>&1 | tail -n 240 | tee -a "$TEST_LOG"
  echo "=== process + memory snapshot ===" | tee -a "$TEST_LOG"
  timeout 8s adb shell ps -A -o PID,PPID,STAT,NAME 2>&1 | grep -E "PID|artistscene|surfaceflinger|zygote" | tee -a "$TEST_LOG"
  timeout 8s adb shell cat /proc/meminfo 2>&1 | head -n 32 | tee -a "$TEST_LOG"

  adb_bounded shell getprop ro.build.fingerprint | tee -a "$TEST_LOG"
  adb_bounded shell getprop ro.hardware.egl | tee -a "$TEST_LOG"
  adb_bounded shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tail -n 8 | tee -a "$TEST_LOG"
  capture_screen "$FAILURE_PNG"
  adb_bounded shell dumpsys SurfaceFlinger | grep -m3 -E "GLES|OpenGL|Display" | tee -a "$TEST_LOG"
  timeout 15s adb logcat -d -v threadtime | grep -Ei \
    'MiseRuntime|filament|gltfio|egl|surface|sceneview|fatal exception|fatal signal|anr|artistscene|AndroidRuntime|lowmemory|lmkd' | tail -n 500 | tee -a "$TEST_LOG"
  set -e
}

fail() {
  echo "ERROR: $*" >&2
  diagnostics
  exit 1
}

wait_for_log() {
  local description="$1"
  local needle="$2"
  for _ in $(seq 1 60); do
    refresh_logcat
    if grep -Fq "$needle" "$LOGCAT"; then
      echo "Reached runtime state: $description"
      return 0
    fi
    require_process_alive "$description"
    sleep 1
  done
  fail "Timed out waiting for runtime state: $description"
}

wait_for_log_count() {
  local description="$1"
  local needle="$2"
  local expected="$3"
  for _ in $(seq 1 60); do
    refresh_logcat
    local count
    count="$(grep -Fc "$needle" "$LOGCAT" || true)"
    if [ "$count" -ge "$expected" ]; then
      echo "Reached runtime state: $description"
      return 0
    fi
    require_process_alive "$description"
    sleep 1
  done
  fail "Timed out waiting for runtime state: $description"
}

wait_for_moved_transform() {
  local description="$1"
  for _ in $(seq 1 60); do
    refresh_logcat
    if python3 - "$LOGCAT" <<'PY'
import re
import sys

try:
    text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
except OSError:
    raise SystemExit(1)
values = re.findall(r"MiseRuntime: transform prop=fixture-boombox x=(-?\d+\.\d+)", text)
raise SystemExit(0 if values and float(values[-1]) >= 0.15 else 1)
PY
    then
      echo "Reached runtime state: $description"
      return 0
    fi
    require_process_alive "$description"
    sleep 1
  done
  fail "Timed out waiting for a meaningful object transform: $description"
}

wait_for_autosaved_moved_transform() {
  local description="$1"
  for _ in $(seq 1 60); do
    refresh_logcat
    if python3 - "$LOGCAT" <<'PY'
import re
import sys

try:
    text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
except OSError:
    raise SystemExit(1)
values = re.findall(
    r"MiseRuntime: scene-autosaved project=feasibility-stage x=(-?\d+\.\d+)",
    text,
)
raise SystemExit(0 if values and float(values[-1]) >= 0.15 else 1)
PY
    then
      echo "Reached runtime state: $description"
      return 0
    fi
    require_process_alive "$description"
    sleep 1
  done
  fail "Timed out waiting for moved transform autosave: $description"
}

tag_coords() {
  local tag="$1"
  python3 - "$XML" "$tag" <<'PY'
import re
import sys
import subprocess
import xml.etree.ElementTree as ET

xml_path, tag = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if node.attrib.get("resource-id") != tag:
        continue
    if node.attrib.get("clickable") != "true":
        raise SystemExit(f"{tag} is exposed but is not clickable")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        raise SystemExit(f"{tag} has invalid bounds: {node.attrib.get('bounds')}")
    left, top, right, bottom = map(int, match.groups())
    # wm size reports the panel's natural orientation, not the rotated display.
    size = subprocess.check_output(["adb", "shell", "wm", "size"], text=True)
    dimensions = re.search(r"(\d+)x(\d+)", size)
    if dimensions:
        width, height = map(int, dimensions.groups())
        if root.attrib.get("rotation") in ("1", "3"):
            width, height = height, width
        center_x, center_y = (left + right) // 2, (top + bottom) // 2
        if not (0 <= center_x < width and 0 <= center_y < height):
            raise SystemExit(f"{tag} is outside the visible display bounds: {center_x},{center_y}")
    if node.attrib.get("visible-to-user") == "false":
        raise SystemExit(f"{tag} is not currently visible to the user")
    print((left + right) // 2, (top + bottom) // 2)
    break
else:
    raise SystemExit(f"{tag} was not found in the UiAutomator hierarchy")
PY
}

text_row_coords() {
  local expected_text="$1"
  python3 - "$XML" "$expected_text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, expected = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") != expected:
        continue
    # Android framework widgets such as DocumentsUI's SAVE button can carry
    # their own text and click action. Start at the matching node before
    # walking ancestors so both native widgets and Compose text-in-row
    # semantics resolve to a real tappable target.
    parent = node
    while parent is not None and parent.attrib.get("clickable") != "true":
        parent = next((candidate for candidate in root.iter("node") if parent in list(candidate)), None)
    if parent is None:
        raise SystemExit(f"Text row {expected!r} has no clickable parent")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", parent.attrib.get("bounds", ""))
    if not match:
        raise SystemExit(f"Text row {expected!r} has invalid bounds: {parent.attrib.get('bounds')}")
    left, top, right, bottom = map(int, match.groups())
    if parent.attrib.get("visible-to-user") == "false":
        raise SystemExit(f"Text row {expected!r} is not visible to the user")
    print((left + right) // 2, (top + bottom) // 2)
    break
else:
    raise SystemExit(f"Text row {expected!r} was not found in the UiAutomator hierarchy")
PY
}

text_contains_coords() {
  local expected_text="$1"
  python3 - "$XML" "$expected_text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, expected = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
nodes = list(root.iter("node"))
parents = {child: parent for parent in nodes for child in list(parent)}

def center(node):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match or node.attrib.get("visible-to-user") == "false":
        return None
    left, top, right, bottom = map(int, match.groups())
    return (left + right) // 2, (top + bottom) // 2

# Prefer real visible text over accessibility descriptions. Modern DocumentsUI
# exposes a clickable preview icon whose description also contains the file name;
# tapping that previews the file instead of selecting it.
for node in nodes:
    if expected not in node.attrib.get("text", ""):
        continue
    parent = node
    while parent is not None and parent.attrib.get("clickable") != "true":
        parent = parents.get(parent)
    target = parent if parent is not None else node
    coords = center(target)
    if coords is not None:
        print(*coords)
        raise SystemExit(0)

# Fall back to content descriptions for controls that genuinely have no text.
for node in nodes:
    if expected not in node.attrib.get("content-desc", ""):
        continue
    parent = node
    while parent is not None and parent.attrib.get("clickable") != "true":
        parent = parents.get(parent)
    if parent is None:
        continue
    coords = center(parent)
    if coords is not None:
        print(*coords)
        raise SystemExit(0)

raise SystemExit(f"No visible row contains {expected!r}")
PY
}

description_coords() {
  local expected_description="$1"
  python3 - "$XML" "$expected_description" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, expected = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
nodes = list(root.iter("node"))
for node in nodes:
    if node.attrib.get("content-desc") != expected:
        continue
    parent = node
    while parent is not None and parent.attrib.get("clickable") != "true":
        parent = next((candidate for candidate in nodes if parent in list(candidate)), None)
    if parent is None:
        raise SystemExit(f"Accessibility action {expected!r} has no clickable parent")
    bounds = parent.attrib.get("bounds", "").strip("[]").replace("][", ",")
    try:
        left, top, right, bottom = map(int, bounds.split(","))
    except ValueError:
        raise SystemExit(f"Accessibility action {expected!r} has invalid bounds: {parent.attrib.get('bounds')}")
    if parent.attrib.get("visible-to-user") == "false":
        raise SystemExit(f"Accessibility action {expected!r} is not visible to the user")
    print((left + right) // 2, (top + bottom) // 2)
    break
else:
    raise SystemExit(f"Accessibility action {expected!r} was not found")
PY
}

tap_coords() {
  local label="$1"
  local coords="$2"
  local x y
  read -r x y <<<"$coords"
  echo "Tapping $label at $x,$y"
  adb_bounded shell input tap "$x" "$y"
}

swipe_coords() {
  local label="$1"
  local coords="$2"
  local distance="$3"
  local x y
  read -r x y <<<"$coords"
  echo "Dragging $label from $x,$y by $distance pixels"
  adb_bounded shell input swipe "$x" "$y" "$((x + distance))" "$y" 700
}

swipe_tool_rail_left() {
  local width height
  read -r width height < <(adb_bounded shell wm size | python3 -c 'import re,sys; m=re.search(r"(\d+)x(\d+)",sys.stdin.read()); print(*(m.groups() if m else ("360","800")))')
  local y=$((height * 92 / 100))
  local start_x=$((width * 88 / 100))
  local end_x=$((width * 12 / 100))
  adb_bounded shell input swipe "$start_x" "$y" "$end_x" "$y" 400
}

swipe_joint_strip_left() {
  local width height
  read -r width height < <(adb_bounded shell wm size | python3 -c 'import re,sys; m=re.search(r"(\d+)x(\d+)",sys.stdin.read()); print(*(m.groups() if m else ("360","800")))')
  local y=$((height * 55 / 100))
  adb_bounded shell input swipe "$((width * 88 / 100))" "$y" "$((width * 12 / 100))" "$y" 400
}

swipe_modal_sheet_up() {
  local width height
  read -r width height < <(adb_bounded shell wm size | python3 -c 'import re,sys; m=re.search(r"(\d+)x(\d+)",sys.stdin.read()); print(*(m.groups() if m else ("360","800")))')
  # Keep vertical sheet-scroll gestures in the left content gutter. On the
  # 320x640 CI viewport, the old 50%/78% start point landed directly on the
  # timeline scrubber and changed the playhead instead of scrolling.
  local x=$((width * 6 / 100))
  # Scroll in short steps so tiny timeline key rows cannot be skipped between
  # UiAutomator snapshots on the 320x640 API 26/36 validation viewport.
  local start_y=$((height * 82 / 100))
  local end_y=$((height * 66 / 100))
  adb_bounded shell input swipe "$x" "$start_y" "$x" "$end_y" 350
}

swipe_modal_sheet_down() {
  local width height
  read -r width height < <(adb_bounded shell wm size | python3 -c 'import re,sys; m=re.search(r"(\\d+)x(\\d+)",sys.stdin.read()); print(*(m.groups() if m else ("360","800")))')
  local x=$((width * 6 / 100))
  local start_y=$((height * 66 / 100))
  local end_y=$((height * 82 / 100))
  adb_bounded shell input swipe "$x" "$start_y" "$x" "$end_y" 350
}

find_visible_tag_by_scrolling_back() {
  local tag="$1"
  local attempts="${2:-6}"
  for _ in $(seq 1 "$attempts"); do
    dump_window_once || return 1
    if visible_tag_present "$tag"; then
      return 0
    fi
    swipe_modal_sheet_down
    sleep 0.6
  done
  return 1
}

find_tag_by_scrolling() {
  local tag="$1"
  local attempts="${2:-6}"
  local coords=""
  for _ in $(seq 1 "$attempts"); do
    dump_window_once || return 1
    if coords="$(tag_coords "$tag" 2>/dev/null)"; then
      printf '%s\n' "$coords"
      return 0
    fi
    swipe_modal_sheet_up
    sleep 0.6
  done
  return 1
}

visible_tag_present() {
  local tag="$1"
  python3 - "$XML" "$tag" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
tag = sys.argv[2]
for node in root.iter("node"):
    if node.attrib.get("resource-id") == tag and node.attrib.get("visible-to-user") != "false":
        raise SystemExit(0)
raise SystemExit(1)
PY
}

find_visible_tag_by_scrolling() {
  local tag="$1"
  local attempts="${2:-6}"
  for _ in $(seq 1 "$attempts"); do
    dump_window_once || return 1
    if visible_tag_present "$tag"; then
      return 0
    fi
    swipe_modal_sheet_up
    sleep 0.6
  done
  return 1
}

visible_text_present() {
  local expected="$1"
  python3 - "$XML" "$expected" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
expected = sys.argv[2]
for node in root.iter("node"):
    if node.attrib.get("text") == expected and node.attrib.get("visible-to-user") != "false":
        raise SystemExit(0)
raise SystemExit(1)
PY
}

find_visible_text_by_scrolling() {
  local expected="$1"
  local attempts="${2:-6}"
  for _ in $(seq 1 "$attempts"); do
    dump_window_once || return 1
    if visible_text_present "$expected"; then
      return 0
    fi
    swipe_modal_sheet_up
    sleep 0.6
  done
  return 1
}

find_text_by_scrolling() {
  local label="$1"
  local attempts="${2:-6}"
  local coords=""
  for _ in $(seq 1 "$attempts"); do
    dump_window_once || return 1
    if coords="$(text_row_coords "$label" 2>/dev/null)"; then
      printf '%s\n' "$coords"
      return 0
    fi
    swipe_modal_sheet_up
    sleep 0.6
  done
  return 1
}

find_text_by_scrolling_back() {
  local label="$1"
  local attempts="${2:-6}"
  local coords=""
  for _ in $(seq 1 "$attempts"); do
    dump_window_once || return 1
    if coords="$(text_row_coords "$label" 2>/dev/null)"; then
      printf '%s\n' "$coords"
      return 0
    fi
    swipe_modal_sheet_down
    sleep 0.6
  done
  return 1
}

dismiss_modal_sheet() {
  local label="$1"
  local close_tag="${2:-}"
  dump_window_once || fail "Could not inspect $label before dismissing it"

  local close_coords=""
  if [ -n "$close_tag" ]; then
    close_coords="$(tag_coords "$close_tag" 2>/dev/null || true)"
  fi
  if [ -z "$close_coords" ]; then
    close_coords="$(description_coords "Close" 2>/dev/null || description_coords "Close sheet" 2>/dev/null || true)"
  fi

  # Mise exposes an explicit close button inside every full-height studio sheet.
  # Prefer that reachable target because Material's own sheet semantics can sit
  # inside the status-bar edge on API 26/36 emulator geometries.
  if [ -n "$close_coords" ]; then
    tap_coords "Close $label" "$close_coords"
    for _ in $(seq 1 20); do
      sleep 0.25
      if dump_window_once; then
        if [ -n "$close_tag" ]; then
          if ! tag_coords "$close_tag" >/dev/null 2>&1; then
            echo "Dismissed modal sheet with explicit close control: $label"
            return 0
          fi
        elif ! grep -Fq 'content-desc="Close sheet"' "$XML"; then
          echo "Dismissed modal sheet with close control: $label"
          return 0
        fi
      fi
    done
  fi

  adb_bounded shell input keyevent KEYCODE_BACK
  for _ in $(seq 1 20); do
    sleep 0.25
    if dump_window_once; then
      if [ -n "$close_tag" ]; then
        if ! tag_coords "$close_tag" >/dev/null 2>&1; then
          echo "Dismissed modal sheet with Back: $label"
          return 0
        fi
      elif ! grep -Fq 'content-desc="Close sheet"' "$XML"; then
        echo "Dismissed modal sheet with Back: $label"
        return 0
      fi
    fi
  done

  fail "$label did not dismiss with its explicit close control or Android Back"
}

echo "Build real debug APK" | tee "$TEST_LOG"
gradle :app:assembleDebug --stacktrace >>"$TEST_LOG" 2>&1 || {
  cat "$TEST_LOG"
  exit 1
}
test -s "$APK" || fail "Debug APK was not produced"

echo "Install real APK on Android API $API_LEVEL" | tee -a "$TEST_LOG"
adb_bounded install -r -t "$APK" >>"$TEST_LOG" 2>&1
adb_bounded shell pm clear "$APP_ID" >>"$TEST_LOG" 2>&1 || true
adb_bounded shell mkdir -p /sdcard/Download
adb_bounded push app/src/main/assets/models/color_cube.glb "$IMPORT_FIXTURE_DEVICE" >>"$TEST_LOG" 2>&1
adb_bounded push app/src/main/assets/models/cesium_man.glb /sdcard/Download/ActStudioRig.glb >>"$TEST_LOG" 2>&1
python3 - <<'PYGLTF'
import struct,json,base64
raw=open('app/src/main/assets/models/cesium_man.glb','rb').read()
offset=12
while offset<len(raw):
 length,kind=struct.unpack_from('<II',raw,offset);chunk=raw[offset+8:offset+8+length]
 if kind==0x4e4f534a: root=json.loads(chunk)
 elif kind==0x004e4942: binary=chunk
 offset+=8+length
root['buffers'][0]['uri']='data:application/octet-stream;base64,'+base64.b64encode(binary).decode()
open('ActStudioRig.gltf','w').write(json.dumps(root))
PYGLTF
adb_bounded push ActStudioRig.gltf /sdcard/Download/ActStudioRig.gltf >>"$TEST_LOG" 2>&1

adb_bounded shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/ActStudioRig.glb >>"$TEST_LOG" 2>&1 || true
adb_bounded shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$IMPORT_FIXTURE_DEVICE" >>"$TEST_LOG" 2>&1 || true

timeout 10s adb logcat -c || true
trap stop_logcat_capture EXIT

echo "Renderer backend diagnostics:" | tee -a "$TEST_LOG"
adb_bounded shell getprop ro.hardware.egl | tee -a "$TEST_LOG" || true
adb_bounded shell dumpsys SurfaceFlinger | grep -m2 -E "GLES|OpenGL" | tee -a "$TEST_LOG" || true

echo "Launch Mise as a normal app process" | tee -a "$TEST_LOG"
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
require_process_alive "initial app launch"

dump_window_once || fail "Could not capture the project browser hierarchy"
grep -Fq "Recent projects" "$XML" || fail "Project browser was not shown on launch"
PROJECT_OPEN_COORDS="$(tag_coords "project-open-feasibility-stage")" \
  || fail "Starter scene could not be opened from the project browser"
capture_screen "$PROJECT_BROWSER_PNG" || fail "Could not capture the project browser screenshot"
tap_coords "starter scene" "$PROJECT_OPEN_COORDS"
require_process_alive "opening the starter scene"

wait_for_log "bundled GLB loaded" "MiseRuntime: asset-loaded name=Boom Box"
wait_for_log "first renderer frame" "MiseRuntime: renderer-first-frame"
wait_for_log "rigged character visibly ready" "MiseRuntime: rig-ready actor=fixture-cesium-man bones=19"
sleep 3

# Perform exactly one accessibility traversal on the live renderer. This proves
# that real Compose controls are exposed/clickable without repeatedly attaching
# UiAutomation while TextureView/Filament is processing scene mutations.
dump_window_once || fail "Could not capture the live Mise UiAutomator hierarchy"
SAVE_COORDS="$(tag_coords "save-project")" || fail "save-project was not exposed as a clickable control"
ADD_COORDS="$(tag_coords "add-object")" || fail "add-object was not exposed as a clickable control"
MOVE_TOOL_COORDS="$(tag_coords "tool-move")" || fail "Move tool was not exposed as a clickable control"
ROTATE_TOOL_COORDS="$(tag_coords "tool-rotate")" || fail "Rotate tool was not exposed as a clickable control"
SCALE_TOOL_COORDS="$(tag_coords "tool-scale")" || fail "Scale tool was not exposed as a clickable control"
SCENE_COORDS="$(tag_coords "scene-hierarchy")" || fail "Scene hierarchy control was not exposed"
capture_screen "$STARTUP_PNG" || fail "Could not capture the loaded editor screenshot"
capture_screen "$SELECTED_PNG" || fail "Could not capture the selected-object screenshot"

tap_coords "Move tool" "$MOVE_TOOL_COORDS"
dump_window_once || fail "Could not inspect selected-object move gizmo"
GIZMO_COORDS="$(tag_coords "gizmo-move-x")" || fail "Move X gizmo was not exposed as a direct manipulation handle"
capture_screen "$TRANSFORM_PNG" || fail "Could not capture the move gizmo screenshot"
swipe_coords "Move X gizmo" "$GIZMO_COORDS" 50
wait_for_moved_transform "gizmo drag changed the selected object X position"
wait_for_autosaved_moved_transform "gizmo drag persisted through autosave"
sleep 1

tap_coords "Rotate tool" "$ROTATE_TOOL_COORDS"
sleep 1
capture_screen "$ROTATE_PNG" || fail "Could not capture the rotate gizmo screenshot"

tap_coords "Scale tool" "$SCALE_TOOL_COORDS"
sleep 1
capture_screen "$SCALE_PNG" || fail "Could not capture the scale gizmo screenshot"

RAIL_PAGE_COORDS="$(tag_coords "tool-rail-page")" || fail "Editor tool rail paging control was not exposed"
tap_coords "More tools" "$RAIL_PAGE_COORDS"
dump_window_once || fail "Could not inspect the secondary editor tool rail"
POSE_COORDS="$(tag_coords "pose-tools")" || fail "Pose tool was not visible on the secondary rail"
tag_coords "camera-tools" >/dev/null || fail "Camera tool was not visible on the secondary rail"
tag_coords "light-tools" >/dev/null || fail "Light tool was not visible on the secondary rail"
capture_screen "$MORE_TOOLS_PNG" || fail "Could not capture the secondary editor tool rail"
RAIL_PAGE_COORDS="$(tag_coords "tool-rail-page")" || fail "Core tool rail paging control was not exposed"
tap_coords "Core tools" "$RAIL_PAGE_COORDS"
dump_window_once || fail "Could not inspect the core editor tool rail"
ADD_COORDS="$(tag_coords "add-object")" || fail "Add control was not visible after returning to the core tool page"

tap_coords "add-object" "$ADD_COORDS"
wait_for_log "Add sheet opened" "MiseRuntime: add-sheet-open"
sleep 1
dump_window_once || fail "Could not inspect the actor asset browser"
text_row_coords "Starter" >/dev/null || fail "Starter asset tab was not exposed as a tappable control"
text_row_coords "My Assets" >/dev/null || fail "My Assets tab was not exposed as a tappable control"
text_row_coords "Import" >/dev/null || fail "Import asset tab was not exposed as a tappable control"
capture_screen "$ADD_PNG" || fail "Could not capture the Add sheet screenshot"
MY_ASSETS_TAB_COORDS="$(text_row_coords "My Assets")" || fail "My Assets tab was not tappable"
tap_coords "My Assets tab" "$MY_ASSETS_TAB_COORDS"
sleep 1
capture_screen "$ASSET_LIBRARY_PNG" || fail "Could not capture My Assets tab"
dump_window_once || fail "Could not inspect My Assets state"
grep -Fq "Imported models will appear here." "$XML" || fail "Empty My Assets state was not exposed"

IMPORT_TAB_COORDS="$(text_row_coords "Import")" || fail "Import tab was not tappable"
tap_coords "Import tab" "$IMPORT_TAB_COORDS"
sleep 1
dump_window_once || fail "Could not inspect Import tab"
IMPORT_MODEL_COORDS="$(tag_coords "import-model" 2>/dev/null || text_row_coords "Import prop" 2>/dev/null || true)"
test -n "$IMPORT_MODEL_COORDS" || fail "Visible Import prop action was not tappable in the Import tab"
tap_coords "Import model" "$IMPORT_MODEL_COORDS"
sleep 1
dump_window_once || fail "Android document picker did not open for model import"

IMPORT_FILE_COORDS="$(text_contains_coords "$IMPORT_FIXTURE_NAME" 2>/dev/null || true)"
if [ -z "$IMPORT_FILE_COORDS" ]; then
  ROOTS_COORDS="$(description_coords "Show roots" 2>/dev/null || true)"
  if [ -n "$ROOTS_COORDS" ]; then
    tap_coords "document roots" "$ROOTS_COORDS"
    sleep 1
    dump_window_once || fail "Could not inspect Android document roots"
    DOWNLOADS_COORDS="$(text_row_coords "Downloads" 2>/dev/null || text_contains_coords "Downloads" 2>/dev/null || true)"
    if [ -n "$DOWNLOADS_COORDS" ]; then
      tap_coords "Downloads" "$DOWNLOADS_COORDS"
      sleep 1
      dump_window_once || fail "Could not inspect Downloads in Android document picker"
    fi
  fi
  IMPORT_FILE_COORDS="$(text_contains_coords "$IMPORT_FIXTURE_NAME" 2>/dev/null || true)"
fi
test -n "$IMPORT_FILE_COORDS" || fail "User-selected GLB fixture was not visible in Android's document picker"
tap_coords "user-selected GLB fixture" "$IMPORT_FILE_COORDS"
wait_for_log "user-selected GLB imported and rendered" "MiseRuntime: asset-loaded name=ActStudioImportCube"
sleep 1

dump_window_once || fail "Could not inspect prop inspector control"
INSPECTOR_COORDS="$(tag_coords "inspector")" || fail "Prop inspector unavailable"
tap_coords "Prop inspector" "$INSPECTOR_COORDS"
sleep 1
SCALE_COORDS="$(find_tag_by_scrolling "tool-scale" 6)" || fail "Scale inspector unavailable"
tap_coords "Scale attached prop" "$SCALE_COORDS"
for axis in x y z; do
  find_tag_by_scrolling "numeric-$axis" 6 >/dev/null || fail "Scale axis unavailable: $axis"
  dump_window_once || fail "Could not inspect scale axis"
  upper_axis="$(printf '%s' "$axis" | tr '[:lower:]' '[:upper:]')"
  DECREASE_COORDS="$(description_coords "Decrease $upper_axis")" || fail "Scale decrease control unavailable"
  for _ in 1 2 3 4 5 6 7 8; do
    tap_coords "Reduce prop $upper_axis size" "$DECREASE_COORDS"
  done
done
dismiss_modal_sheet "prop inspector" "close-context-sheet"

dump_window_once || fail "Could not inspect editor after user-selected model import"
ADD_COORDS="$(tag_coords "add-object")" || fail "Add control disappeared after user-selected model import"
tap_coords "Add after import" "$ADD_COORDS"
sleep 1
dump_window_once || fail "Could not inspect asset browser after import"
MY_ASSETS_TAB_COORDS="$(text_row_coords "My Assets")" || fail "My Assets tab was not reachable after import"
tap_coords "My Assets after import" "$MY_ASSETS_TAB_COORDS"
sleep 1
find_visible_text_by_scrolling "ActStudioImportCube" 6 || fail "Imported model was not persisted into My Assets"
capture_screen "$IMPORTED_ASSET_PNG" || fail "Could not capture imported My Assets state"
dismiss_modal_sheet "asset browser after import" "close-add-sheet"
sleep 1

dump_window_once || fail "Could not inspect editor after imported asset browser"
ADD_COORDS="$(tag_coords "add-object")" || fail "Add control was not visible after imported asset browser"
tap_coords "add-object for light proof" "$ADD_COORDS"
sleep 1
dump_window_once || fail "Could not inspect asset browser for light proof"
STARTER_TAB_COORDS="$(text_row_coords "Starter")" || fail "Starter tab was not tappable after import proof"
tap_coords "Starter tab" "$STARTER_TAB_COORDS"
sleep 1
SPOT_COORDS="$(find_tag_by_scrolling "add-spot-light" 7 || text_row_coords "Spot")" || fail "Spot light control was not exposed in the scrollable Add sheet"
test -n "$SPOT_COORDS" || fail "Spot light control did not provide tappable coordinates"
dismiss_modal_sheet "asset browser" "close-add-sheet"
sleep 1

tap_coords "Scene hierarchy" "$SCENE_COORDS"
sleep 1
capture_screen "$HIERARCHY_PNG" || fail "Could not capture the scene hierarchy sheet"
dump_window_once || fail "Could not inspect the scene hierarchy"
CHARACTER_COORDS="$(text_row_coords "Cesium Man")" || fail "Rigged character was not visible in the scene hierarchy"
tap_coords "Rigged character" "$CHARACTER_COORDS"
sleep 1
PARENT_ROOT_COORDS="$(find_tag_by_scrolling "parent-scene-root" 5 || text_row_coords "Scene root")" || fail "Scene hierarchy did not expose parent controls in its scrollable content"
test -n "$PARENT_ROOT_COORDS" || fail "Scene-root parent control did not provide tappable coordinates"
dismiss_modal_sheet "scene hierarchy" "close-context-sheet"
sleep 1

dump_window_once || fail "Could not inspect the editor tools after closing hierarchy"
INSPECTOR_COORDS="$(tag_coords "inspector")" || {
  swipe_tool_rail_left
  dump_window_once || fail "Could not inspect the editor tool strip"
  INSPECTOR_COORDS="$(tag_coords "inspector")" || fail "Inspector control was not exposed"
}
tap_coords "Inspector" "$INSPECTOR_COORDS"
sleep 1
capture_screen "$INSPECTOR_PNG" || fail "Could not capture the inspector sheet"
adb_bounded shell input keyevent KEYCODE_BACK
sleep 1

dump_window_once || fail "Could not inspect the pose tool entry"
RAIL_PAGE_COORDS="$(tag_coords "tool-rail-page")" || fail "Editor tool rail paging control was not exposed"
tap_coords "More tools for Pose" "$RAIL_PAGE_COORDS"
dump_window_once || fail "Could not inspect the Pose tool page"
frame_selected_character
capture_screen "artist-scene-studio-${API_TAG}-character-before-fk.png" || fail "Could not capture rest skin before FK"
POSE_COORDS="$(tag_coords "pose-tools")" || fail "Pose tool entry was not exposed"
tap_coords "Pose tools" "$POSE_COORDS"
wait_for_log "real glTF skin joints discovered" "MiseRuntime: rig-ready actor=fixture-cesium-man bones=19 posed=0"
dump_window_once || fail "Could not verify the real-rig Pose sheet"
ELBOW_MARKER_COORDS="$(tag_coords "joint-marker-skeleton-arm-joint-r-2")" || fail "Projected elbow joint marker was not exposed in the viewport"
sleep 1
capture_screen "$POSE_PNG" || fail "Could not capture the pose controls sheet"
swipe_coords "drag right elbow joint on Character A" "$ELBOW_MARKER_COORDS" 55
wait_for_log "Character A real skin pose applied" "MiseRuntime: rig-ready actor=fixture-cesium-man bones=19 posed=1"
dump_window_once || fail "Could not inspect selected elbow controls for Character A"
grep -Fq "Right Elbow" "$XML" || fail "Dragging the elbow did not select its contextual pose controls"
PLUS_COORDS="$(description_coords "Increase joint rotation")" || fail "Selected elbow rotation control was not exposed"
tap_coords "Increase Character A elbow rotation" "$PLUS_COORDS"
tap_coords "Increase Character A elbow rotation again" "$PLUS_COORDS"
sleep 1
dump_window_once || fail "Could not inspect the updated Character A elbow control"
grep -Fq "Right Elbow" "$XML" || fail "Character A selected joint label disappeared after rotation"
capture_screen "artist-scene-studio-${API_TAG}-elbow-selected.png" || fail "Could not capture selected elbow controls"

FK_DEGREES="$(python3 - "$XML" <<'PYCODE'
import re,sys,xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter("node"):
    match = re.fullmatch(r"Right Elbow · (-?\d+)°", node.attrib.get("text", ""))
    if match:
        print(match.group(1)); break
else:
    raise SystemExit("Selected elbow angle missing")
PYCODE
)" || fail "Could not read the authored FK angle"
assert_elbow_angle() {
  dump_window_once || fail "Could not inspect elbow angle"
  python3 - "$XML" "$1" <<'PYCODE' || fail "Elbow angle did not match undo/redo/reset state"
import sys,xml.etree.ElementTree as ET
expected = f"Right Elbow · {sys.argv[2]}°"
if not any(node.attrib.get("text") == expected for node in ET.parse(sys.argv[1]).iter("node")):
    raise SystemExit(f"Missing expected angle: {expected}")
PYCODE
}
UNDO_COORDS="$(tag_coords "undo")" || fail "Undo was unavailable after FK edit"
tap_coords "Undo last elbow rotation" "$UNDO_COORDS"
sleep 1
assert_elbow_angle "$((FK_DEGREES - 10))"
REDO_COORDS="$(tag_coords "redo")" || fail "Redo was unavailable after FK undo"
tap_coords "Redo last elbow rotation" "$REDO_COORDS"
sleep 1
assert_elbow_angle "$FK_DEGREES"
RESET_COORDS="$(tag_coords "pose-reset-joint")" || fail "Reset joint was unavailable"
tap_coords "Reset selected elbow" "$RESET_COORDS"
sleep 1
assert_elbow_angle 0
UNDO_COORDS="$(tag_coords "undo")" || fail "Reset joint was not undoable"
tap_coords "Undo elbow reset" "$UNDO_COORDS"
sleep 1
assert_elbow_angle "$FK_DEGREES"
RESET_COORDS="$(tag_coords "pose-reset-all")" || fail "Reset pose was unavailable"
tap_coords "Reset character pose" "$RESET_COORDS"
sleep 1
assert_elbow_angle 0
UNDO_COORDS="$(tag_coords "undo")" || fail "Reset pose was not undoable"
tap_coords "Undo pose reset" "$UNDO_COORDS"
sleep 1
assert_elbow_angle "$FK_DEGREES"
capture_screen "artist-scene-studio-${API_TAG}-fk-restored.png" || fail "Could not capture restored FK state"

# Switch the same real rig into viewport IK and move the right wrist as a two-bone chain.
dump_window_once || fail "Could not inspect IK mode control for Character A"
IK_MODE_COORDS="$(tag_coords "pose-mode-ik" 2>/dev/null || text_row_coords "IK")" || fail "Pose sheet did not expose IK mode"
tap_coords "Enable Character A IK" "$IK_MODE_COORDS"
sleep 1
dump_window_once || fail "Could not inspect Character A IK end effectors"
WRIST_MARKER_COORDS="$(tag_coords "joint-marker-skeleton-arm-joint-r-3")" || fail "Character A right-wrist IK marker was not exposed"
swipe_coords "drag right wrist with IK on Character A" "$WRIST_MARKER_COORDS" 45
wait_for_log "Character A two-bone IK pose applied" "MiseRuntime: rig-ready actor=fixture-cesium-man bones=19 posed=2"
dump_window_once || fail "Could not inspect the Character A IK result"
grep -Fq "Right Wrist" "$XML" || fail "IK wrist drag did not select the right-wrist contextual controls"
capture_screen "artist-scene-studio-${API_TAG}-ik-wrist.png" || fail "Could not capture the real-rig IK result"
dump_window_once || fail "Could not inspect the pose close control"
POSE_DONE_COORDS="$(tag_coords "pose-done")" || fail "Pose Done control was not exposed"
tap_coords "Finish Character A pose" "$POSE_DONE_COORDS"
sleep 1

# Select a second instance of the same skinned GLB and pose it independently.
dump_window_once || fail "Could not inspect the two-character hierarchy"
SCENE_COORDS="$(tag_coords "scene-hierarchy")" || fail "Scene hierarchy control was not exposed for Character B"
tap_coords "Scene hierarchy for Character B" "$SCENE_COORDS"
sleep 1
dump_window_once || fail "Could not inspect the two-character hierarchy rows"
# Actor names are user-editable. Select Character B through its durable scene ID
# so the proof exercises the actual hierarchy row instead of depending on text
# coordinates that can be obscured by an IME on API 26.
if ! find_visible_tag_by_scrolling_back "actor-fixture-cesium-man-b" 6; then
  find_visible_tag_by_scrolling "actor-fixture-cesium-man-b" 6     || fail "Second rigged character was not visible in the hierarchy"
fi
dump_window_once || fail "Could not inspect Character B hierarchy row"
CHARACTER_B_COORDS="$(tag_coords "actor-fixture-cesium-man-b")"   || fail "Second rigged character did not expose a stable hierarchy target"
tap_coords "Rigged character B" "$CHARACTER_B_COORDS"
wait_for_log "Character B selected in hierarchy" "MiseRuntime: editor-change reason=hierarchy-select selected=fixture-cesium-man-b"
sleep 1
dismiss_modal_sheet "scene hierarchy for Character B" "close-context-sheet"
sleep 1
dump_window_once || fail "Could not inspect Pose entry for Character B"
frame_selected_character
POSE_COORDS="$(tag_coords "pose-tools")" || fail "Pose tool entry was not visible for Character B"
tap_coords "Pose tools for Character B" "$POSE_COORDS"
wait_for_log "second real glTF skeleton discovered" "MiseRuntime: rig-ready actor=fixture-cesium-man-b bones=19 posed=0"
dump_window_once || fail "Could not inspect Character B pose markers"
JOINT_MODE_COORDS="$(tag_coords "pose-mode-joint" 2>/dev/null || text_row_coords "Joint")" || fail "Character B pose sheet did not expose Joint mode"
tap_coords "Use Joint mode for Character B" "$JOINT_MODE_COORDS"
sleep 1
dump_window_once || fail "Could not inspect Character B joint-mode pose markers"
ELBOW_MARKER_COORDS="$(tag_coords "joint-marker-skeleton-arm-joint-r-2")" || fail "Character B elbow marker was not exposed"
capture_screen "artist-scene-studio-${API_TAG}-two-character-pose.png" || fail "Could not capture two-character pose view"
swipe_coords "drag right elbow joint on Character B" "$ELBOW_MARKER_COORDS" -55
wait_for_log "Character B real skin pose applied independently" "MiseRuntime: rig-ready actor=fixture-cesium-man-b bones=19 posed=1"
dump_window_once || fail "Could not inspect selected elbow controls for Character B"
grep -Fq "Right Elbow" "$XML" || fail "Character B elbow drag did not select its own joint controls"
MINUS_COORDS="$(description_coords "Decrease joint rotation")" || fail "Character B elbow rotation control was not exposed"
tap_coords "Decrease Character B elbow rotation" "$MINUS_COORDS"
tap_coords "Decrease Character B elbow rotation again" "$MINUS_COORDS"
sleep 1
dump_window_once || fail "Could not inspect Character B pose close"
POSE_DONE_COORDS="$(tag_coords "pose-done")" || fail "Character B Pose Done unavailable"
tap_coords "Finish Character B pose" "$POSE_DONE_COORDS"
sleep 1
capture_screen "$POSED_PNG" || fail "Could not capture both independently posed characters"

# Attach the real SAF-imported prop to Character A's right hand, then move its arm.
dump_window_once || fail "Could not inspect hierarchy for attachment"
SCENE_COORDS="$(tag_coords "scene-hierarchy")" || fail "Hierarchy unavailable for attachment"
tap_coords "Hierarchy for prop attachment" "$SCENE_COORDS"
sleep 1
PROP_COORDS="$(find_text_by_scrolling_back "ActStudioImportCube" 6 || find_text_by_scrolling "ActStudioImportCube" 6)" || fail "Imported prop was missing from hierarchy"
tap_coords "Imported prop" "$PROP_COORDS"
sleep 1
dismiss_modal_sheet "prop hierarchy" "close-context-sheet"
dump_window_once || fail "Could not inspect hierarchy after prop scaling"
SCENE_COORDS="$(tag_coords "scene-hierarchy")" || fail "Hierarchy unavailable after scaling"
tap_coords "Attach prop" "$SCENE_COORDS"
sleep 1
CHARACTER_COORDS="$(find_tag_by_scrolling "attachment-character-fixture-cesium-man" 8)" || fail "Attachment character picker unavailable"
tap_coords "Attach to Character A" "$CHARACTER_COORDS"
WRIST_COORDS="$(find_tag_by_scrolling "attachment-bone-skeleton-arm-joint-r-3" 8)" || fail "Right-hand attachment control unavailable"
tap_coords "Attach prop to right hand" "$WRIST_COORDS"
wait_for_log "rendered prop attached to hand" "bone-attachment-followed actor=import-"
capture_screen "artist-scene-studio-${API_TAG}-attachment-hierarchy.png" || fail "Could not capture attachment relationship"
CHARACTER_COORDS="$(hierarchy_actor_coords "fixture-cesium-man")" || fail "Character A hierarchy row unavailable"
tap_coords "Character A with hand prop" "$CHARACTER_COORDS"
dismiss_modal_sheet "attachment hierarchy" "close-context-sheet"
frame_selected_character
POSE_COORDS="$(tag_coords "pose-tools")" || fail "Pose unavailable for attached prop"
tap_coords "Pose attached hand" "$POSE_COORDS"
sleep 1
dump_window_once || fail "Could not inspect attached hand pose"
JOINT_COORDS="$(tag_coords "pose-mode-joint")" || fail "Joint mode unavailable for attachment proof"
tap_coords "Joint mode for attached hand" "$JOINT_COORDS"
sleep 1
dump_window_once || fail "Could not inspect attachment elbow"
ELBOW_COORDS="$(tag_coords "joint-marker-skeleton-arm-joint-r-2")" || fail "Attachment elbow marker unavailable"
tap_coords "Select attached arm elbow" "$ELBOW_COORDS"
sleep 1
dump_window_once || fail "Could not inspect attached elbow controls"
PLUS_COORDS="$(description_coords "Increase joint rotation")" || fail "Attachment elbow rotation unavailable"
capture_screen "artist-scene-studio-${API_TAG}-hand-prop-before.png" || fail "Could not capture hand prop before arm rotation"
tap_coords "Rotate arm carrying prop" "$PLUS_COORDS"
sleep 1
capture_screen "artist-scene-studio-${API_TAG}-hand-prop-after.png" || fail "Could not capture hand prop after arm rotation"
dump_window_once || fail "Could not inspect attachment pose close"
DONE_COORDS="$(tag_coords "pose-done")" || fail "Pose close unavailable after attachment"
tap_coords "Finish attachment pose" "$DONE_COORDS"
dump_window_once || fail "Could not inspect hierarchy after attachment posing"
SCENE_COORDS="$(tag_coords "scene-hierarchy")" || fail "Hierarchy unavailable after attachment posing"
tap_coords "Return to Character B" "$SCENE_COORDS"
sleep 1
CHARACTER_B_COORDS="$(hierarchy_actor_coords "fixture-cesium-man-b")" || fail "Character B unavailable after attachment"
tap_coords "Character B for timeline" "$CHARACTER_B_COORDS"
dismiss_modal_sheet "hierarchy after attachment" "close-context-sheet"
sleep 1

# Prove authored scene-timeline controls are usable and persist real transform tracks.
dump_window_once || fail "Could not inspect Animation tool"
MOTION_COORDS="$(tag_coords "motion-tools")" || fail "Animation tool was not exposed"
tap_coords "Animation tools" "$MOTION_COORDS"
sleep 1
dump_window_once || fail "Could not inspect scene timeline controls"
KEY_TRANSFORM_COORDS="$(tag_coords "timeline-key-transform" 2>/dev/null || text_row_coords "Key transform")" || fail "Timeline key control was not exposed"
tap_coords "Key Character B transform at zero" "$KEY_TRANSFORM_COORDS"
sleep 1
for _ in 1 2 3 4; do
  dump_window_once || fail "Could not inspect timeline playhead controls"
  FORWARD_COORDS="$(tag_coords "timeline-forward" 2>/dev/null || text_row_coords "+0.25 s")" || fail "Timeline forward control was not exposed"
  tap_coords "Advance timeline playhead" "$FORWARD_COORDS"
done
sleep 1
dump_window_once || fail "Could not inspect advanced timeline playhead"
grep -Fq "1.00 s /" "$XML" || fail "Timeline playhead did not advance to one second"
KEY_TRANSFORM_COORDS="$(tag_coords "timeline-key-transform" 2>/dev/null || text_row_coords "Key transform")" || fail "Timeline key control disappeared"
tap_coords "Key Character B transform at one second" "$KEY_TRANSFORM_COORDS"
sleep 1
# Reopen the sheet before verifying the summary instead of swiping downward on the
# ModalBottomSheet. A downward drag can dismiss the sheet and leak through to the
# 3D camera on both API 26 and API 36.
dismiss_modal_sheet "animation after transform keying" "close-context-sheet"
sleep 1
dump_window_once || fail "Could not inspect Animation tool after transform keying"
MOTION_COORDS="$(tag_coords "motion-tools")" || fail "Animation tool disappeared after transform keying"
tap_coords "Reopen Animation for transform key proof" "$MOTION_COORDS"
sleep 1
find_visible_text_by_scrolling "Keys · 0.00s  1.00s" 4 \
  || fail "Timeline did not expose the authored zero- and one-second transform keys after reopening"

# Start playback from a fresh top-of-sheet state too. This keeps proof gestures
# away from the viewport and makes the compact phone sheet deterministic.
dismiss_modal_sheet "animation transform key proof" "close-context-sheet"
sleep 1
dump_window_once || fail "Could not inspect Animation tool before playback"
MOTION_COORDS="$(tag_coords "motion-tools")" || fail "Animation tool disappeared before playback"
tap_coords "Reopen Animation for playback" "$MOTION_COORDS"
sleep 1
dump_window_once || fail "Could not inspect scene timeline playback control"
PLAY_TIMELINE_COORDS="$(tag_coords "timeline-play" 2>/dev/null || text_row_coords "Play scene")" || fail "Scene timeline playback control was not exposed"
tap_coords "Play authored scene timeline" "$PLAY_TIMELINE_COORDS"
sleep 1
dump_window_once || fail "Could not inspect active scene timeline playback"
grep -Fq 'text="Stop"' "$XML" || fail "Scene timeline did not enter playback state"
PLAY_TIMELINE_COORDS="$(tag_coords "timeline-play" 2>/dev/null || text_row_coords "Stop")" || fail "Scene timeline stop control disappeared"
tap_coords "Stop authored scene timeline" "$PLAY_TIMELINE_COORDS"
sleep 1
# Playback advances while UiAutomator is inspecting the active controls. Put the
# playhead back at a deterministic 1.00 s before authoring the pose key instead
# of assuming Stop preserves the old one-second position.
dump_window_once || fail "Could not inspect timeline controls before pose keying"
BACK_COORDS="$(tag_coords "timeline-back" 2>/dev/null || text_row_coords "-0.25 s")" || fail "Timeline back control disappeared after playback"
FORWARD_COORDS="$(tag_coords "timeline-forward" 2>/dev/null || text_row_coords "+0.25 s")" || fail "Timeline forward control disappeared after playback"
for _ in $(seq 1 24); do
  tap_coords "Rewind timeline toward zero" "$BACK_COORDS"
done
for _ in 1 2 3 4; do
  tap_coords "Return timeline to one second" "$FORWARD_COORDS"
done
sleep 1
dump_window_once || fail "Could not inspect reset timeline playhead"
grep -Fq "1.00 s /" "$XML" || fail "Timeline playhead was not restored to one second before pose keying"
POSE_KEY_COORDS="$(find_text_by_scrolling "Key pose" 6)" || fail "Animation sheet did not expose character pose keying"
tap_coords "Key Character B pose at one second" "$POSE_KEY_COORDS"
sleep 1
find_visible_text_by_scrolling "Pose keys · 1.00s" 4 \
  || fail "Character pose key was not visibly recorded at one second"
dismiss_modal_sheet "animation" "close-context-sheet"
sleep 1

dump_window_once || fail "Could not inspect reference view control"
REFERENCE_COORDS="$(tag_coords "reference-mode")" || fail "Reference mode control was not exposed"
tap_coords "Reference mode" "$REFERENCE_COORDS"
sleep 1
capture_screen "$REFERENCE_PNG" || fail "Could not capture the clean reference viewport"
dump_window_once || fail "Could not inspect clean reference controls"
EXPORT_SCENE_COORDS="$(tag_coords "export-scene-png" 2>/dev/null || text_row_coords "Export PNG")" || fail "Clean reference mode did not expose PNG export"
tap_coords "Export PNG" "$EXPORT_SCENE_COORDS"
sleep 2
dump_window_once || fail "Could not inspect Android's PNG destination picker"
EXPORT_SAVE_COORDS="$(text_row_coords "Save" 2>/dev/null || text_row_coords "SAVE" 2>/dev/null)"   || fail "Android document picker did not expose a Save action for PNG export"
tap_coords "Save exported PNG" "$EXPORT_SAVE_COORDS"
wait_for_log "clean PNG export completed" "MiseRuntime: export-png-complete project=feasibility-stage"
sleep 1
dump_window_once || fail "Could not inspect export completion state"
grep -Fq "PNG saved" "$XML" || fail "Clean reference view did not report successful PNG export"
EXIT_REFERENCE_COORDS="$(tag_coords "exit-reference-mode" 2>/dev/null || text_row_coords "Edit scene")" || fail "Reference mode could not be exited after PNG export"
tap_coords "Edit scene" "$EXIT_REFERENCE_COORDS"
sleep 1

dump_window_once || fail "Could not inspect Save control"
SAVE_COORDS="$(tag_coords "save-project")" || fail "Save scene control was not exposed"

tap_coords "save-project" "$SAVE_COORDS"
wait_for_log "scene save completed" "MiseRuntime: scene-saved project=feasibility-stage x="

adb_bounded shell run-as "$APP_ID" cat "$PROJECT_FILE" >"$SAVED_JSON" \
  || fail "Saved scene file could not be read from app storage"
PERSISTED_X="$(python3 - "$SAVED_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    project = json.load(handle)
prop = next(actor for actor in project["actors"] if actor["id"] == "fixture-boombox")
x = float(prop["transform"]["position"]["x"])
if x < 0.15 or x > 0.5:
    raise SystemExit(f"unexpected persisted x={x}")
print(f"{x:.2f}")
PY
)" || fail "Persisted scene did not contain the moved BoomBox fixture"
python3 - "$SAVED_JSON" <<'PY' || fail "Saved scene did not retain the user-selected imported GLB"
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    project = json.load(handle)
actor = next((item for item in project.get("actors", []) if item.get("name") == "ActStudioImportCube"), None)
if actor is None:
    raise SystemExit("imported actor missing from saved scene")
asset = actor.get("asset") or {}
if asset.get("storage") != "PROJECT_FILE":
    raise SystemExit(f"imported actor was not converted to durable project storage: {asset}")
if not str(asset.get("relativePath", "")).startswith("asset-library/payloads/"):
    raise SystemExit(f"unexpected imported payload path: {asset.get('relativePath')}")
checksum = str(asset.get("checksumSha256") or "")
if len(checksum) != 64:
    raise SystemExit(f"missing imported payload checksum: {checksum!r}")
print("Saved durable user-selected import:", asset.get("relativePath"))
PY
python3 - "$SAVED_JSON" "$LOGCAT" <<'PYATTACH' || fail "Hand attachment did not move and persist"
import json,re,sys
project=json.load(open(sys.argv[1]))
prop=next(a for a in project["actors"] if a["name"] == "ActStudioImportCube")
if prop.get("parentId") != "fixture-cesium-man" or not str(prop.get("parentBoneId", "")).endswith("skeleton-arm-joint-r-3"):
    raise SystemExit("Hand attachment relationship missing")
for axis in ("x","y","z"):
    if abs(prop["transform"]["scale"].get(axis,1)-.2) > .01: raise SystemExit("Prop scale not preserved")
lines=open(sys.argv[2],errors="replace").read().splitlines()
pattern=re.compile(r"bone-attachment-followed actor="+re.escape(prop["id"])+r" .* x=([^ ]+) y=([^ ]+) z=([^ ]+)")
positions={tuple(round(float(v),4) for v in m.groups()) for line in lines if (m:=pattern.search(line))}
if len(positions)<2: raise SystemExit("Attached prop did not visibly follow an updated joint transform")
print("Hand attachment persisted; distinct rendered grip positions:",len(positions))
PYATTACH
python3 - "$SAVED_JSON" <<'PY' || fail "Saved scene did not retain two independent character poses"
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    project = json.load(handle)
a = next(actor for actor in project["actors"] if actor["id"] == "fixture-cesium-man")
b = next(actor for actor in project["actors"] if actor["id"] == "fixture-cesium-man-b")
a_joints = (a.get("rig") or {}).get("joints") or {}
b_joints = (b.get("rig") or {}).get("joints") or {}
if len(a_joints) != 2:
    raise SystemExit(f"Character A expected shoulder + elbow after IK; got {a_joints}")
if not any(joint_id.endswith("skeleton-arm-joint-r") for joint_id in a_joints):
    raise SystemExit(f"Character A IK shoulder pose was not persisted: {a_joints}")
if not any(joint_id.endswith("skeleton-arm-joint-r-2") for joint_id in a_joints):
    raise SystemExit(f"Character A right-elbow pose was not persisted: {a_joints}")
if len(b_joints) != 1:
    raise SystemExit(f"Character B expected one independent elbow pose; got {b_joints}")
b_joint_id, b_rotation = next(iter(b_joints.items()))
if not b_joint_id.endswith("skeleton-arm-joint-r-2"):
    raise SystemExit(f"Character B expected right elbow, got {b_joint_id}")
if not any(abs(float(b_rotation.get(axis, 0.0))) >= 9.9 for axis in ("x", "y", "z")):
    raise SystemExit(f"Character B elbow rotation was not applied: {b_rotation}")
print("Saved Character A IK joints:", sorted(a_joints))
print("Saved Character B right-elbow pose:", b_rotation)
if a["rig"] == b["rig"]:
    raise SystemExit("Character A and B unexpectedly share identical pose state")
PY
python3 - "$SAVED_JSON" <<'PY' || fail "Saved scene did not retain authored timeline keys"
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    project = json.load(handle)
tracks = {
    track.get("propertyPath"): track
    for track in project.get("tracks", [])
    if track.get("targetActorId") == "fixture-cesium-man-b"
}
expected = {"transform.position", "transform.rotation", "transform.scale"}
if not expected.issubset(tracks):
    raise SystemExit(f"missing transform tracks: {expected - set(tracks)}")
for path in expected:
    times = [round(float(key["timeSeconds"]), 2) for key in tracks[path].get("keyframes", [])]
    if times != [0.0, 1.0]:
        raise SystemExit(f"{path} expected keys at 0.0 and 1.0 seconds; got {times}")
print("Saved authored Character B transform timeline")
PY
python3 - "$SAVED_JSON" <<'PY' || fail "Saved scene did not retain authored Character B pose timeline"
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    project = json.load(handle)
pose_tracks = [
    track for track in project.get("tracks", [])
    if track.get("targetActorId") == "fixture-cesium-man-b"
    and track.get("propertyPath", "").startswith("rig.joint.")
]
if len(pose_tracks) != 19:
    raise SystemExit(f"expected 19 Character B joint tracks; got {len(pose_tracks)}")
for track in pose_tracks:
    times = [round(float(key["timeSeconds"]), 2) for key in track.get("keyframes", [])]
    if times != [1.0]:
        raise SystemExit(f"{track.get('propertyPath')} expected pose key at 1.0 second; got {times}")
elbow = next(
    (track for track in pose_tracks if track.get("propertyPath", "").endswith("skeleton-arm-joint-r-2")),
    None,
)
if elbow is None:
    raise SystemExit("missing right-elbow pose track")
rotation = elbow["keyframes"][0]["value"].get("rotationEulerDegrees") or {}
if not any(abs(float(rotation.get(axis, 0.0))) >= 9.9 for axis in ("x", "y", "z")):
    raise SystemExit(f"right-elbow pose track did not retain the authored rotation: {rotation}")
print("Saved authored Character B rig pose timeline")
PY
wait_for_log "scene save recorded moved X $PERSISTED_X" "MiseRuntime: scene-saved project=feasibility-stage x=$PERSISTED_X"
capture_screen "$SAVED_PNG" || fail "Could not capture the saved scene screenshot"

refresh_logcat
ATTACHMENT_RESTORE_COUNT="$(( $(grep -c 'bone-attachment-followed actor=import-' "$LOGCAT") + 1 ))"
echo "Force-stop and relaunch to prove process restore" | tee -a "$TEST_LOG"
adb_bounded shell am force-stop "$APP_ID"
sleep 1
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
require_process_alive "restore app launch"

dump_window_once || fail "Could not capture the project browser after process restart"
PROJECT_OPEN_COORDS="$(tag_coords "project-open-feasibility-stage")" \
  || fail "Saved scene was missing from the project browser after process restart"
tap_coords "saved scene after restart" "$PROJECT_OPEN_COORDS"

wait_for_log "saved scene reopened by a fresh process" "MiseRuntime: scene-opened project=feasibility-stage x=$PERSISTED_X"
wait_for_log_count "second GLB load after process restore" "MiseRuntime: asset-loaded name=Boom Box" 2
wait_for_log_count "imported GLB reload after process restore" "MiseRuntime: asset-loaded name=ActStudioImportCube" 2
wait_for_log_count "second renderer frame after process restore" "MiseRuntime: renderer-first-frame" 2
wait_for_log "Character A IK pose restored in fresh process" "MiseRuntime: rig-ready actor=fixture-cesium-man bones=19 posed=2"
wait_for_log "Character B pose restored in fresh process" "MiseRuntime: rig-ready actor=fixture-cesium-man-b bones=19 posed=1"
wait_for_log_count "hand prop follows joint after fresh-process restore" "bone-attachment-followed actor=import-" "$ATTACHMENT_RESTORE_COUNT"
sleep 1
capture_screen "$RESTORED_PNG" || fail "Could not capture the reopened scene screenshot"
python3 scripts/check-viewport-pixels.py "$STARTUP_PNG" "$RESTORED_PNG" "artist-scene-studio-${API_TAG}-character-before-fk.png" "artist-scene-studio-${API_TAG}-fk-restored.png" || fail "Renderer screenshots contain a black viewport"
cp "$RESTORED_PNG" "$PNG"

# Validate responsive chrome and surface resumption on the restored project.
adb_bounded shell settings put system accelerometer_rotation 0
adb_bounded shell settings put system user_rotation 1
sleep 4
require_process_alive "landscape configuration"
dump_window_once || fail "Could not inspect landscape editor"
tag_coords "back-to-projects" >/dev/null || fail "Landscape top bar was clipped"
tag_coords "tool-rail-page" >/dev/null || fail "Landscape tool rail was clipped"
capture_screen "artist-scene-studio-${API_TAG}-landscape.png" || fail "Could not capture landscape editor"
adb_bounded shell settings put system user_rotation 0
sleep 4
adb_bounded shell input keyevent KEYCODE_HOME
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
sleep 2
require_process_alive "background and resume"
capture_screen "artist-scene-studio-${API_TAG}-resumed.png" || fail "Could not capture resumed viewport"

dump_window_once || fail "Could not inspect restored tool rail"
if ! tag_coords "light-tools" >/dev/null; then
  tap_coords "Secondary tools" "$(tag_coords "tool-rail-page")"
  dump_window_once || fail "Could not inspect secondary tools"
fi
tap_coords "Light controls" "$(tag_coords "light-tools")"
sleep 1
dump_window_once || fail "Could not inspect light controls"
find_tag_by_scrolling "light-intensity-up" 6 >/dev/null || fail "Light intensity control was unavailable"
capture_screen "artist-scene-studio-${API_TAG}-light-controls.png" || fail "Could not capture light controls"
tap_coords "Close light controls" "$(find_tag_by_scrolling "close-context-sheet" 6)"
dump_window_once || fail "Could not inspect editor after light controls"
tap_coords "Project browser" "$(tag_coords "back-to-projects")"
sleep 1
dump_window_once || fail "Could not inspect browser for new scene"
tap_coords "Create scene" "$(tag_coords "create-project")"
sleep 1
dump_window_once || fail "Could not inspect new scene dialog"
tap_coords "Confirm new scene" "$(tag_coords "confirm-project-name")"
sleep 2
require_process_alive "new empty scene"
dump_window_once || fail "Could not inspect empty scene"
python3 - "$XML" <<'PYVIEWPORT' || fail "Empty scene viewport was absent"
import sys,xml.etree.ElementTree as ET
assert any(n.get('resource-id')=='scene-viewport' for n in ET.parse(sys.argv[1]).iter('node'))
PYVIEWPORT
tag_coords "add-object" >/dev/null || fail "Empty scene Add control was absent"
capture_screen "artist-scene-studio-${API_TAG}-empty-scene.png" || fail "Could not capture empty scene"
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-landscape.png" "artist-scene-studio-${API_TAG}-resumed.png" "artist-scene-studio-${API_TAG}-empty-scene.png" || fail "Responsive/resumed viewport was black"

# Prove the entire posing loop with a character imported through Android SAF.
NEW_PROJECT_NAME="$(adb_bounded shell run-as "$APP_ID" ls files/projects | tr -d '\r' | python3 -c 'import sys; print(next(s.strip() for s in sys.stdin if s.strip().endswith(".scene.json") and s.strip() != "feasibility-stage.scene.json"))')" || fail "New scene file was missing"
NEW_PROJECT_ID="${NEW_PROJECT_NAME%.scene.json}"
NEW_PROJECT_FILE="files/projects/$NEW_PROJECT_NAME"
dump_window_once || fail "Could not inspect empty scene Add control"
tap_coords "Add imported character" "$(tag_coords "add-object")"
sleep 1
dump_window_once || fail "Could not inspect character import sheet"
tap_coords "Import tab" "$(find_text_by_scrolling "Import" 6)"
sleep 1
tap_coords "Character role" "$(find_tag_by_scrolling "import-role-character" 8)"
dump_window_once || fail "Could not inspect character import action"
tap_coords "Import character file" "$(find_tag_by_scrolling "import-model" 8)"
sleep 1
dump_window_once || fail "Could not inspect rigged character picker"
RIG_FILE_COORDS="$(text_contains_coords "ActStudioRig.glb" 2>/dev/null || true)"
if [ -z "$RIG_FILE_COORDS" ]; then
  tap_coords "Document roots" "$(description_coords "Show roots")"
  sleep 1
  dump_window_once || fail "Could not inspect document roots for character"
  tap_coords "Downloads" "$(text_row_coords "Downloads" 2>/dev/null || text_contains_coords "Downloads")"
  sleep 1
  dump_window_once || fail "Could not inspect character file in Downloads"
  RIG_FILE_COORDS="$(text_contains_coords "ActStudioRig.glb")" || fail "Rigged GLB was missing from picker"
fi
tap_coords "Rigged GLB" "$RIG_FILE_COORDS"
wait_for_log "SAF imported character rendered" "MiseRuntime: asset-loaded name=ActStudioRig"
sleep 3
adb_bounded shell run-as "$APP_ID" cat "$NEW_PROJECT_FILE" > artist-scene-studio-saf-character-before.json
RIG_ACTOR_ID="$(python3 - <<'PYRIG'
import json
p=json.load(open('artist-scene-studio-saf-character-before.json'))
a=next(a for a in p['actors'] if a['name']=='ActStudioRig')
assert a['kind']=='CHARACTER',a['kind']
print(a['id'])
PYRIG
)" || fail "SAF model was not imported as a character"
wait_for_log "SAF rig bound to rendered skeleton" "MiseRuntime: rig-ready actor=$RIG_ACTOR_ID bones=19"
capture_screen "artist-scene-studio-${API_TAG}-saf-character-rest.png" || fail "Could not capture imported rest character"
dump_window_once || fail "Could not inspect imported character tools"
tap_coords "Pose tool page" "$(tag_coords "tool-rail-page")"
dump_window_once || fail "Could not inspect imported character Pose tool"
tap_coords "Pose imported character" "$(tag_coords "pose-tools")"
sleep 1
dump_window_once || fail "Could not inspect imported character joints"
RIG_ELBOW="$(tag_coords "joint-marker-skeleton-arm-joint-r-2")" || fail "Imported character elbow was not exposed"
swipe_coords "Bend imported character elbow" "$RIG_ELBOW" 75
sleep 1
dump_window_once || fail "Could not inspect imported elbow rotation"
grep -Fq "Right Elbow" "$XML" || fail "Imported character elbow was not selected"
RIG_FK_DEGREES="$(python3 - "$XML" <<'PYRIG'
import sys,re,xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter('node'):
 m=re.fullmatch(r'Right Elbow · (-?\d+)°',n.attrib.get('text',''))
 if m:
  assert abs(int(m[1]))>=20
  print(m[1]);break
else: raise SystemExit('Imported elbow angle was missing')
PYRIG
)" || fail "Imported FK gesture did not rotate the elbow"
capture_screen "artist-scene-studio-${API_TAG}-saf-character-bent.png" || fail "Could not capture visible imported skin bend"
tap_coords "Undo imported FK gesture" "$(tag_coords "undo")"
sleep 1
assert_elbow_angle 0
tap_coords "Redo imported FK gesture" "$(tag_coords "redo")"
sleep 1
assert_elbow_angle "$RIG_FK_DEGREES"
tap_coords "Close imported Pose controls" "$(tag_coords "pose-done")"
dump_window_once || fail "Could not inspect imported project Save control"
tap_coords "Save imported pose" "$(tag_coords "save-project")"
wait_for_log "Imported pose saved" "MiseRuntime: scene-saved project=$NEW_PROJECT_ID"
adb_bounded shell run-as "$APP_ID" cat "$NEW_PROJECT_FILE" > artist-scene-studio-saf-character-saved.json
# Remove only the disposable picker fixture, proving the project's durable copy is used.
adb_bounded shell toybox unlink /sdcard/Download/ActStudioRig.glb
refresh_logcat
RIG_RESTORE_COUNT="$(( $(grep -c "rig-ready actor=$RIG_ACTOR_ID bones=19 posed=1" "$LOGCAT") + 1 ))"
adb_bounded shell am force-stop "$APP_ID"
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
sleep 1
dump_window_once || fail "Could not inspect imported project after process death"
tap_coords "Reopen imported pose project" "$(tag_coords "project-open-$NEW_PROJECT_ID")"
wait_for_log_count "Imported pose rendered after fresh process" "rig-ready actor=$RIG_ACTOR_ID bones=19 posed=1" "$RIG_RESTORE_COUNT"
sleep 2
capture_screen "artist-scene-studio-${API_TAG}-saf-character-reopened.png" || fail "Could not capture reopened imported character"
adb_bounded shell run-as "$APP_ID" cat "$NEW_PROJECT_FILE" > artist-scene-studio-saf-character-reopened.json
python3 - <<'PYRIG' || fail "Imported pose was not exactly restored"
import json
saved=json.load(open('artist-scene-studio-saf-character-saved.json'))
restored=json.load(open('artist-scene-studio-saf-character-reopened.json'))
a=next(a for a in saved['actors'] if a['name']=='ActStudioRig')
b=next(a for a in restored['actors'] if a['id']==a['id'])
assert a['asset']==b['asset'] and a['rig']==b['rig'] and a['transform']==b['transform']
assert len(a['rig']['joints'])==1
assert any(abs(float(v))>=20 for rotation in a['rig']['joints'].values() for v in rotation.values())
assert saved['cameras']==restored['cameras']
print('SAF character pose, transform, durable asset and camera restored exactly')
PYRIG
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-saf-character-rest.png" "artist-scene-studio-${API_TAG}-saf-character-bent.png" "artist-scene-studio-${API_TAG}-saf-character-reopened.png" || fail "Imported character viewport was black"
# Validate a self-contained JSON glTF through the same SAF rig pipeline.
dump_window_once || fail "Could not inspect reopened character Add control"
tap_coords "Add glTF character" "$(tag_coords "add-object")"
sleep 1
dump_window_once || fail "Could not inspect glTF import sheet"
tap_coords "Import glTF tab" "$(find_text_by_scrolling "Import" 6)"
tap_coords "glTF character role" "$(find_tag_by_scrolling "import-role-character" 8)"
tap_coords "Import glTF file" "$(find_tag_by_scrolling "import-model" 8)"
sleep 1
dump_window_once || fail "Could not inspect glTF file picker"
tap_coords "Rigged glTF document" "$(text_contains_coords "ActStudioRig.gltf")"
# The filename has the same actor name; a second load establishes the new document path.
wait_for_log_count "SAF glTF rendered" "MiseRuntime: asset-loaded name=ActStudioRig" 3
sleep 3
adb_bounded shell run-as "$APP_ID" cat "$NEW_PROJECT_FILE" > artist-scene-studio-saf-gltf.json
GLTF_ACTOR_ID="$(python3 - <<'PYGLTF'
import json
p=json.load(open('artist-scene-studio-saf-gltf.json'))
a=next(a for a in p['actors'] if a.get('asset',{}).get('format')=='gltf')
assert a['kind']=='CHARACTER'
assert len(a['rigDefinition']['bones'])==19
print(a['id'])
PYGLTF
)" || fail "Imported glTF skeleton was not discovered"
wait_for_log "glTF rig bound to rendered mesh" "rig-ready actor=$GLTF_ACTOR_ID bones=19"
dump_window_once || fail "Could not inspect glTF placement"
tap_coords "glTF placement inspector" "$(tag_coords "inspector")"
sleep 1
tap_coords "glTF Move inspector" "$(find_tag_by_scrolling "tool-move" 6)"
find_tag_by_scrolling "numeric-x" 6 >/dev/null || fail "glTF position X unavailable"
dump_window_once || fail "Could not inspect glTF placement X"
GLTF_MOVE_X="$(description_coords "Increase X")"
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do tap_coords "Separate glTF character" "$GLTF_MOVE_X"; done
dismiss_modal_sheet "glTF inspector" "close-context-sheet"
dump_window_once || fail "Could not inspect glTF tool page"
tap_coords "glTF Pose tool page" "$(tag_coords "tool-rail-page")"
dump_window_once || fail "Could not inspect glTF Pose control"
frame_selected_character
tap_coords "Pose glTF character" "$(tag_coords "pose-tools")"
sleep 1
dump_window_once || fail "Could not inspect glTF elbow"
swipe_coords "Bend glTF elbow" "$(tag_coords "joint-marker-skeleton-arm-joint-r-2")" 75
wait_for_log "glTF skin deformation applied" "rig-ready actor=$GLTF_ACTOR_ID bones=19 posed=1"
sleep 1
capture_screen "artist-scene-studio-${API_TAG}-saf-gltf-bent.png" || fail "Could not capture glTF pose"
tap_coords "Close glTF Pose" "$(tag_coords "pose-done")"
dump_window_once || fail "Could not inspect glTF Save control"
tap_coords "Save glTF pose" "$(tag_coords "save-project")"
sleep 2
adb_bounded shell run-as "$APP_ID" cat "$NEW_PROJECT_FILE" > artist-scene-studio-saf-gltf.json
python3 - <<'PYGLTF' || fail "Posing glTF changed the existing GLB actor"
import json
saved=json.load(open('artist-scene-studio-saf-character-saved.json'))
current=json.load(open('artist-scene-studio-saf-gltf.json'))
a=next(a for a in saved['actors'] if a['name']=='ActStudioRig')
b=next(b for b in current['actors'] if b['id']==a['id'])
assert a['rig']==b['rig'] and a['transform']==b['transform']
g=next(a for a in current['actors'] if a.get('asset',{}).get('format')=='gltf')
assert len(g['rig']['joints'])==1
print('SAF GLB and glTF characters have independent rendered/persisted poses')
PYGLTF
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-saf-gltf-bent.png" || fail "glTF viewport was black"

# A missing durable asset must leave its actor and authored pose in the project.
MISSING_ASSET_PATH="$(python3 - <<'PYMISSING'
import json
p=json.load(open('artist-scene-studio-saf-character-saved.json'))
print(next(a['asset']['relativePath'] for a in p['actors'] if a['name']=='ActStudioRig'))
PYMISSING
)" || fail "Could not identify the disposable imported asset"
adb_bounded shell am force-stop "$APP_ID"
adb_bounded shell run-as "$APP_ID" mv "files/$MISSING_ASSET_PATH" "files/$MISSING_ASSET_PATH.qa-missing"
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
sleep 1
dump_window_once || fail "Could not inspect missing-asset project browser"
tap_coords "Open scene with missing asset" "$(tag_coords "project-open-$NEW_PROJECT_ID")"
wait_for_log "Missing asset reported without crashing" "asset-failed $MISSING_ASSET_PATH"
sleep 2
require_process_alive "missing imported character"
dump_window_once || fail "Could not inspect missing-asset feedback"
grep -Fq "Its file is missing or unavailable" "$XML" || fail "Missing model was not identified in the editor"
capture_screen "artist-scene-studio-${API_TAG}-missing-asset.png" || fail "Could not capture missing-asset feedback"
adb_bounded shell run-as "$APP_ID" cat "$NEW_PROJECT_FILE" > artist-scene-studio-saf-missing.json
python3 - <<'PYMISSING' || fail "Missing asset destroyed authored project data"
import json
before=json.load(open('artist-scene-studio-saf-gltf.json'))
after=json.load(open('artist-scene-studio-saf-missing.json'))
assert len(before['actors'])==len(after['actors'])
for a in before['actors']:
 b=next(b for b in after['actors'] if b['id']==a['id'])
 assert a.get('asset')==b.get('asset') and a.get('rig')==b.get('rig') and a.get('transform')==b.get('transform')
print('Missing asset retained all actor, asset, transform and pose state')
PYMISSING
adb_bounded shell run-as "$APP_ID" mv "files/$MISSING_ASSET_PATH.qa-missing" "files/$MISSING_ASSET_PATH"

# Return to the browser for the separate instrumentation lifecycle.
dump_window_once || fail "Could not inspect imported editor Back control"
tap_coords "Close imported project" "$(tag_coords "back-to-projects")"
sleep 1

# Browser operations must preserve authored data and confirm destructive deletion.
dump_window_once || fail "Could not inspect scene menu for duplicate"
tap_coords "Scene options" "$(tag_coords "project-menu-$NEW_PROJECT_ID")"
dump_window_once || fail "Could not inspect Duplicate scene action"
tap_coords "Duplicate scene" "$(tag_coords "duplicate-project-$NEW_PROJECT_ID")"
sleep 1
COPY_PROJECT_NAME="$(adb_bounded shell run-as "$APP_ID" ls files/projects | tr -d '\r' | python3 -c 'import sys; excluded=set(sys.argv[1:]); print(next(s.strip() for s in sys.stdin if s.strip().endswith(".scene.json") and s.strip() not in excluded))' feasibility-stage.scene.json "$NEW_PROJECT_NAME")" || fail "Duplicated scene file was missing"
COPY_PROJECT_ID="${COPY_PROJECT_NAME%.scene.json}"
adb_bounded shell run-as "$APP_ID" cat "files/projects/$COPY_PROJECT_NAME" > artist-scene-studio-saf-duplicate.json
python3 - <<'PYCOPY' || fail "Duplicated scene lost its rig or attachments"
import json
original=json.load(open('artist-scene-studio-saf-gltf.json'))
copy=json.load(open('artist-scene-studio-saf-duplicate.json'))
assert original['id']!=copy['id']
assert original['actors']==copy['actors'] and original['cameras']==copy['cameras']
PYCOPY
dump_window_once || fail "Could not inspect duplicate scene menu"
tap_coords "Duplicate scene options" "$(tag_coords "project-menu-$COPY_PROJECT_ID")"
dump_window_once || fail "Could not inspect Rename action"
tap_coords "Rename duplicated scene" "$(tag_coords "rename-project-$COPY_PROJECT_ID")"
sleep 1
dump_window_once || fail "Could not inspect rename dialog"
tap_coords "Scene name" "$(tag_coords "project-name-input")"
adb_bounded shell input text renamed
adb_bounded shell input keyevent KEYCODE_BACK
dump_window_once || fail "Could not inspect rename confirmation"
tap_coords "Confirm rename" "$(tag_coords "confirm-project-name")"
sleep 1
adb_bounded shell run-as "$APP_ID" cat "files/projects/$COPY_PROJECT_NAME" > artist-scene-studio-saf-renamed.json
python3 - <<'PYCOPY' || fail "Renaming duplicated scene lost its state"
import json
before=json.load(open('artist-scene-studio-saf-duplicate.json'))
after=json.load(open('artist-scene-studio-saf-renamed.json'))
assert before['id']==after['id'] and before['name']!=after['name']
assert before['actors']==after['actors'] and before['cameras']==after['cameras']
PYCOPY
dump_window_once || fail "Could not inspect renamed scene menu"
tap_coords "Renamed scene options" "$(tag_coords "project-menu-$COPY_PROJECT_ID")"
dump_window_once || fail "Could not inspect Delete scene action"
tap_coords "Delete duplicate" "$(tag_coords "delete-project-$COPY_PROJECT_ID")"
sleep 1
dump_window_once || fail "Could not inspect deletion confirmation"
tag_coords "confirm-delete-project" >/dev/null || fail "Project deletion bypassed confirmation"
capture_screen "artist-scene-studio-${API_TAG}-delete-confirmation.png" || fail "Could not capture deletion confirmation"
tap_coords "Confirm duplicate deletion" "$(tag_coords "confirm-delete-project")"
sleep 1
if adb_bounded shell run-as "$APP_ID" test -f "files/projects/$COPY_PROJECT_NAME"; then fail "Confirmed duplicate deletion left its project file"; fi
dump_window_once || fail "Could not inspect final project browser"
tag_coords "project-open-$NEW_PROJECT_ID" >/dev/null || fail "Deleting the copy removed the original"
capture_screen "$PROJECT_BROWSER_PNG" || fail "Could not capture polished project browser"

echo "Run Android instrumentation against the real editor" | tee -a "$TEST_LOG"
gradle :app:connectedDebugAndroidTest --stacktrace >>"$TEST_LOG" 2>&1 || {
  tail -n 120 "$TEST_LOG"
  fail "Android instrumentation failed"
}
adb_bounded pull /sdcard/Android/data/$APP_ID/files/instrumented-viewport.png "artist-scene-studio-${API_TAG}-instrumented.png" >/dev/null 2>&1 || fail "Instrumentation screenshot was missing"
python3 scripts/check-viewport-pixels.py "artist-scene-studio-${API_TAG}-instrumented.png" || fail "Instrumentation viewport was black"

echo "Android API $API_LEVEL renderer smoke passed: real app + user-selected GLB import + renderer frame + transforms + direct pose + IK + timeline + export + save/restore" | tee -a "$TEST_LOG"
