#!/usr/bin/env bash
set -euo pipefail

LOGCAT=artist-scene-studio-api36-logcat.txt
XML=artist-scene-studio-window.xml
PNG=artist-scene-studio-api36.png
TEST_LOG=artist-scene-studio-connected-test.log

adb_bounded() {
  timeout 15s adb "$@"
}

timeout 10s adb logcat -c || true
trap 'timeout 10s adb logcat -d -v threadtime > "$LOGCAT" 2>&1 || true' EXIT

echo "Renderer backend diagnostics:"
adb_bounded shell getprop ro.hardware.egl || true
adb_bounded shell dumpsys SurfaceFlinger | grep -m1 -E "GLES|OpenGL" || true

set +e
timeout 10m gradle :app:connectedDebugAndroidTest --stacktrace > "$TEST_LOG" 2>&1
test_status=$?
set -e
cat "$TEST_LOG"

if [ "$test_status" -ne 0 ]; then
  echo "API 36 connected renderer test failed with exit code $test_status"
  adb_bounded devices -l || true
  adb_bounded shell getprop ro.build.fingerprint || true
  adb_bounded shell getprop ro.hardware.egl || true
  adb_bounded shell dumpsys SurfaceFlinger | grep -m2 -E "GLES|OpenGL|Display" || true
  adb_bounded shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tail -n 5 || true
  adb_bounded shell uiautomator dump /sdcard/artist-scene-studio-failure.xml >/tmp/mise-uiautomator-failure.txt 2>&1 || true
  adb_bounded pull /sdcard/artist-scene-studio-failure.xml "$XML" >/dev/null 2>&1 || true
  cat /tmp/mise-uiautomator-failure.txt || true
  cat "$XML" || true
  find app/build/outputs app/build/reports/androidTests/connected -maxdepth 7 -type f \
    \( -name '*.xml' -o -name '*.txt' \) -print -exec tail -n 120 {} \; 2>/dev/null || true
  timeout 15s adb logcat -d -v threadtime | grep -Ei \
    'filament|gltfio|egl|surface|sceneview|fatal exception|artist-scene|AndroidRuntime' | tail -n 240 || true
  exit "$test_status"
fi

adb_bounded shell am force-stop studio.artistscene.app
adb_bounded shell monkey -p studio.artistscene.app 1 >/tmp/mise-monkey.txt 2>&1
cat /tmp/mise-monkey.txt

ready=0
for attempt in $(seq 1 30); do
  adb_bounded shell uiautomator dump /sdcard/artist-scene-studio-window.xml >/tmp/mise-uiautomator.txt 2>&1 || true
  adb_bounded pull /sdcard/artist-scene-studio-window.xml "$XML" >/dev/null 2>&1 || true

  if [ -s "$XML" ] &&
     rg -q "Restored saved scene" "$XML" &&
     rg -q "X 0.25" "$XML" &&
     rg -q "Loaded GLB" "$XML" &&
     rg -q "Renderer loop active" "$XML"; then
    ready=1
    break
  fi

  if ! adb_bounded shell pidof studio.artistscene.app >/dev/null 2>&1; then
    echo "Artist Scene Studio process exited during renderer restore proof"
    cat /tmp/mise-uiautomator.txt || true
    exit 1
  fi
  sleep 2
done

if [ "$ready" != 1 ]; then
  echo "Renderer restore proof never reached its expected UI state"
  cat "$XML" || true
  timeout 15s adb logcat -d -v threadtime | tail -n 400 || true
  exit 1
fi

adb_bounded shell screencap -p /sdcard/artist-scene-studio-api36.png
adb_bounded pull /sdcard/artist-scene-studio-api36.png "$PNG"
test -s "$PNG"
