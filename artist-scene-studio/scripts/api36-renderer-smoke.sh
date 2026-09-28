#!/usr/bin/env bash
set -euo pipefail

APP_ID=studio.artistscene.app
ACTIVITY="$APP_ID/.MainActivity"
APK=app/build/outputs/apk/debug/app-debug.apk
PROJECT_FILE=files/projects/feasibility-stage.scene.json

LOGCAT=artist-scene-studio-api36-logcat.txt
XML=artist-scene-studio-window.xml
PNG=artist-scene-studio-api36.png
TEST_LOG=artist-scene-studio-connected-test.log
STARTUP_PNG=artist-scene-studio-api36-startup.png
TRANSFORM_PNG=artist-scene-studio-api36-transform.png
SAVED_PNG=artist-scene-studio-api36-saved.png
RESTORED_PNG=artist-scene-studio-api36-restored.png
FAILURE_PNG=artist-scene-studio-api36-failure.png
SAVED_JSON=artist-scene-studio-saved-scene.json

adb_bounded() {
  timeout 20s adb "$@"
}

stop_logcat_capture() {
  if [ -n "${LOGCAT_PID:-}" ]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi
  if [ ! -s "$LOGCAT" ]; then
    timeout 10s adb logcat -d -v threadtime > "$LOGCAT" 2>&1 || true
  fi
}

dump_window() {
  timeout 20s adb shell uiautomator dump /sdcard/artist-scene-studio-window.xml >/tmp/mise-uiautomator.txt 2>&1 || return 1
  timeout 20s adb pull /sdcard/artist-scene-studio-window.xml "$XML" >/dev/null 2>&1 || return 1
  test -s "$XML"
}

process_alive() {
  adb_bounded shell pidof "$APP_ID" >/dev/null 2>&1
}

capture_screen() {
  local output="$1"
  local remote="/sdcard/$(basename "$output")"
  adb_bounded shell screencap -p "$remote"
  adb_bounded pull "$remote" "$output" >/dev/null
  test -s "$output"
}

diagnostics() {
  set +e
  echo "=== API 36 renderer diagnostics ==="
  adb_bounded devices -l
  adb_bounded shell getprop ro.build.fingerprint
  adb_bounded shell getprop ro.hardware.egl
  adb_bounded shell dumpsys SurfaceFlinger | grep -m3 -E "GLES|OpenGL|Display"
  adb_bounded shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tail -n 8
  dump_window
  cat /tmp/mise-uiautomator.txt
  cat "$XML"
  capture_screen "$FAILURE_PNG"
  timeout 15s adb logcat -d -v threadtime | grep -Ei \
    'filament|gltfio|egl|surface|sceneview|fatal exception|fatal signal|anr|artistscene|AndroidRuntime|lowmemory|lmkd' | tail -n 400
  set -e
}

fail() {
  echo "ERROR: $*" >&2
  diagnostics
  exit 1
}

wait_for_ui() {
  local description="$1"
  shift
  for _ in $(seq 1 60); do
    if dump_window; then
      local found=1
      for needle in "$@"; do
        if ! grep -Fq "$needle" "$XML"; then
          found=0
          break
        fi
      done
      if [ "$found" -eq 1 ]; then
        echo "Reached UI state: $description"
        return 0
      fi
    fi
    process_alive || fail "Mise process exited while waiting for: $description"
    sleep 1
  done
  fail "Timed out waiting for UI state: $description"
}

tap_tag() {
  local tag="$1"
  dump_window || fail "Could not dump hierarchy before tapping $tag"
  local coords
  coords="$(python3 - "$XML" "$tag" <<'PY'
import re
import sys
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
    print((left + right) // 2, (top + bottom) // 2)
    break
else:
    raise SystemExit(f"{tag} was not found in the UiAutomator hierarchy")
PY
)" || fail "Could not resolve clickable bounds for $tag"

  read -r x y <<<"$coords"
  echo "Tapping $tag at $x,$y"
  adb_bounded shell input tap "$x" "$y"
}

echo "Build real debug APK" | tee "$TEST_LOG"
gradle :app:assembleDebug --stacktrace >>"$TEST_LOG" 2>&1 || {
  cat "$TEST_LOG"
  exit 1
}
test -s "$APK" || fail "Debug APK was not produced"

echo "Install real APK on API 36" | tee -a "$TEST_LOG"
adb_bounded install -r -t "$APK" >>"$TEST_LOG" 2>&1
adb_bounded shell pm clear "$APP_ID" >>"$TEST_LOG" 2>&1 || true

timeout 10s adb logcat -c || true
adb logcat -v threadtime >"$LOGCAT" 2>&1 &
LOGCAT_PID=$!
trap stop_logcat_capture EXIT

echo "Renderer backend diagnostics:" | tee -a "$TEST_LOG"
adb_bounded shell getprop ro.hardware.egl | tee -a "$TEST_LOG" || true
adb_bounded shell dumpsys SurfaceFlinger | grep -m2 -E "GLES|OpenGL" | tee -a "$TEST_LOG" || true

echo "Launch Mise as a normal app process" | tee -a "$TEST_LOG"
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
process_alive || fail "Mise did not remain alive after launch"

wait_for_ui "loaded GLB, live render loop, and accessible controls" \
  "Loaded GLB" \
  "Renderer loop active" \
  'resource-id="move-right"' \
  'resource-id="save-project"'
capture_screen "$STARTUP_PNG"

tap_tag "move-right"
wait_for_ui "scene-owned transform X 0.25" "X 0.25"
capture_screen "$TRANSFORM_PNG"

tap_tag "save-project"
wait_for_ui "saved scene status" "Saved scene"

adb_bounded shell run-as "$APP_ID" cat "$PROJECT_FILE" >"$SAVED_JSON" \
  || fail "Saved scene file could not be read from app storage"
python3 - "$SAVED_JSON" <<'PY' || fail "Persisted scene did not contain X 0.25 for the BoomBox fixture"
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    project = json.load(handle)
prop = next(actor for actor in project["actors"] if actor["id"] == "fixture-boombox")
x = float(prop["transform"]["position"]["x"])
if abs(x - 0.25) > 1e-6:
    raise SystemExit(f"unexpected persisted x={x}")
PY
capture_screen "$SAVED_PNG"

echo "Force-stop and relaunch to prove process restore" | tee -a "$TEST_LOG"
adb_bounded shell am force-stop "$APP_ID"
sleep 1
adb_bounded shell am start -W -n "$ACTIVITY" | tee -a "$TEST_LOG"
process_alive || fail "Mise did not remain alive after restore launch"

wait_for_ui "restored saved scene with a live renderer" \
  "Restored saved scene" \
  "X 0.25" \
  "Loaded GLB" \
  "Renderer loop active"
capture_screen "$RESTORED_PNG"
cp "$RESTORED_PNG" "$PNG"

echo "API 36 renderer smoke passed: real app + GLB + render loop + transform + save + process restore" | tee -a "$TEST_LOG"
