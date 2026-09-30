#!/usr/bin/env bash
set -Eeuo pipefail

APP_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
OUT="$GITHUB_WORKSPACE/cortex-api26-instrumentation.txt"
LOGCAT="$GITHUB_WORKSPACE/cortex-api26-logcat.txt"
UI_DUMP="$GITHUB_WORKSPACE/cortex-api26-ui.xml"
FOREGROUND="$GITHUB_WORKSPACE/cortex-api26-foreground.txt"
DIAGNOSTICS="$GITHUB_WORKSPACE/cortex-api26-diagnostics.txt"
SCREENSHOT="$GITHUB_WORKSPACE/cortex-api26-home.png"
SANITY="$GITHUB_WORKSPACE/cortex-api26-screenshot-sanity.txt"
CONNECTION_SETUP_SCREENSHOT="$GITHUB_WORKSPACE/cortex-connection-setup-emulator.png"

framework_ready() {
  test "$(adb get-state 2>/dev/null || true)" = "device" || return 1
  test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" || return 1
  adb shell pm path android 2>/dev/null | grep -q '^package:' || return 1
  adb shell am get-current-user >/dev/null 2>&1 || return 1
}

wait_for_android() {
  local i consecutive=0
  for i in $(seq 1 90); do
    if framework_ready; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= 3 )); then
        echo "API 26 framework stable."
        return 0
      fi
    else
      consecutive=0
    fi
    sleep 2
  done
  echo "API 26 framework never became stable." >&2
  return 1
}

wake_and_unlock() {
  adb shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
  adb shell svc power stayon true >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
}

capture_failure_diagnostics() {
  {
    echo "===== pid ====="
    adb shell pidof com.night.cortex 2>&1 || true
    echo
    echo "===== resumed activity ====="
    adb shell dumpsys activity activities 2>&1 | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -n 8 || true
    echo
    echo "===== focused window ====="
    adb shell dumpsys window windows 2>&1 | grep -E 'mCurrentFocus|mFocusedApp' | head -n 8 || true
    echo
    echo "===== last ANR ====="
    adb shell dumpsys activity lastanr 2>&1 || true
    echo
    echo "===== process record ====="
    adb shell dumpsys activity processes 2>&1 | grep -A18 -B4 'com.night.cortex' | head -n 120 || true
  } >"$DIAGNOSTICS"
}

validate_png() {
  python3 - "$SCREENSHOT" "$SANITY" <<'PY'
import struct
import sys
import zlib

path, report = sys.argv[1:3]
data = open(path, "rb").read()
if not data.startswith(b"\x89PNG\r\n\x1a\n"):
    raise SystemExit("Screenshot is not a PNG")

pos = 8
width = height = bit_depth = color_type = interlace = None
compressed = bytearray()
while pos + 12 <= len(data):
    length = struct.unpack(">I", data[pos:pos + 4])[0]
    kind = data[pos + 4:pos + 8]
    payload = data[pos + 8:pos + 8 + length]
    pos += 12 + length
    if kind == b"IHDR":
        width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
    elif kind == b"IDAT":
        compressed.extend(payload)
    elif kind == b"IEND":
        break

if not width or not height or bit_depth != 8 or interlace != 0:
    raise SystemExit("Unsupported screenshot PNG")
channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
if channels is None:
    raise SystemExit("Unsupported screenshot color type")

raw = zlib.decompress(bytes(compressed))
stride = width * channels
prior = bytearray(stride)
offset = 0
minimum, maximum, nonblack = 255, 0, 0
unique = set()
sample_step = max(1, (width * height) // 100000)

def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c

for y in range(height):
    filt = raw[offset]
    offset += 1
    scan = bytearray(raw[offset:offset + stride])
    offset += stride
    recon = bytearray(stride)
    for i, value in enumerate(scan):
        left = recon[i-channels] if i >= channels else 0
        up = prior[i]
        up_left = prior[i-channels] if i >= channels else 0
        if filt == 0: out = value
        elif filt == 1: out = (value + left) & 255
        elif filt == 2: out = (value + up) & 255
        elif filt == 3: out = (value + ((left + up)//2)) & 255
        elif filt == 4: out = (value + paeth(left, up, up_left)) & 255
        else: raise SystemExit("Unsupported PNG filter")
        recon[i] = out
    for x in range(width):
        i = x * channels
        if color_type == 0:
            rgb = (recon[i],) * 3
        elif color_type == 2:
            rgb = tuple(recon[i:i+3])
        elif color_type == 4:
            rgb = (recon[i],) * 3
        else:
            rgb = tuple(recon[i:i+3])
        brightness = max(rgb)
        minimum = min(minimum, brightness)
        maximum = max(maximum, brightness)
        if brightness > 12:
            nonblack += 1
        if (y * width + x) % sample_step == 0 and len(unique) < 256:
            unique.add(rgb)
    prior = recon

fraction = nonblack / (width * height)
with open(report, "w", encoding="utf-8") as out:
    out.write(f"size={width}x{height}\n")
    out.write(f"brightness_min={minimum}\n")
    out.write(f"brightness_max={maximum}\n")
    out.write(f"nonblack_fraction={fraction:.6f}\n")
    out.write(f"sampled_unique_colors={len(unique)}\n")

if maximum <= 12 or maximum - minimum <= 6 or fraction < 0.01 or len(unique) < 8:
    raise SystemExit("Screenshot failed pixel sanity")
PY
}

wait_for_android
wake_and_unlock

test -s "$APP_APK"
test -s "$TEST_APK"
adb install -r -g "$APP_APK"
adb install -r -g "$TEST_APK"

: >"$OUT"
run_test_class() {
  local class_name="$1"
  local label="$2"
  local temp rc
  temp="$(mktemp)"
  wait_for_android
  wake_and_unlock
  adb logcat -c >/dev/null 2>&1 || true
  set +e
  timeout 8m adb shell am instrument -w -r \
    -e class "$class_name" \
    com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner >"$temp" 2>&1
  rc=$?
  set -e
  {
    echo "===== $label ====="
    cat "$temp"
    echo
  } >>"$OUT"
  cat "$temp"
  rm -f "$temp"
  if (( rc != 0 )); then
    echo "$label instrumentation command failed with exit code $rc." >&2
    return "$rc"
  fi
  if grep -Eqi 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$temp" ||
     ! grep -q '^OK (' "$temp"; then
    echo "$label did not report a clean passing test run." >&2
    cat "$temp" >&2
    return 1
  fi
}

# Run the UI smoke first, before the heavier pairing suite can stress the old
# software-emulated API 26 framework.
run_test_class "com.night.cortex.CortexSmokeTest" "CortexSmokeTest"
run_test_class "com.night.cortex.CortexPairingScreenTest" "CortexPairingScreenTest"
run_test_class "com.night.cortex.CortexPowerControlsTest" "CortexPowerControlsTest"
run_test_class "com.night.cortex.CortexReleaseVisualTest" "CortexReleaseVisualTest"

adb logcat -d -v threadtime >"$LOGCAT" 2>&1 || true
capture_failure_diagnostics

wake_and_unlock
adb shell am force-stop com.night.cortex
adb shell am start -n com.night.cortex/.MainActivity >/dev/null

: >"$FOREGROUND"
for i in $(seq 1 20); do
  activity="$(adb shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -n 4 || true)"
  focus="$(adb shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4 || true)"
  {
    echo "attempt=$i"
    echo "pid=$(adb shell pidof com.night.cortex 2>/dev/null | tr -d '\r' || true)"
    echo "$activity"
    echo "$focus"
  } >"$FOREGROUND"
  if printf '%s\n%s\n' "$activity" "$focus" | grep -q 'com.night.cortex/.MainActivity'; then
    break
  fi
  sleep 1
done
grep -q 'com.night.cortex/.MainActivity' "$FOREGROUND"

systemui_wait_used=0
for i in $(seq 1 12); do
  adb shell rm -f /sdcard/cortex-api26-ui.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump --compressed /sdcard/cortex-api26-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/cortex-api26-ui.xml >"$UI_DUMP" 2>/dev/null || true

  if test -s "$UI_DUMP" &&
     grep -q 'package="com.night.cortex"' "$UI_DUMP" &&
     grep -Eq 'text="Cortex"|content-desc="Cortex"' "$UI_DUMP" &&
     grep -Eq 'text="Console"|content-desc="Console"' "$UI_DUMP"; then
    break
  fi

  # The API 26 Google APIs image can surface a System UI ANR after the long
  # instrumentation session even while Cortex itself is resumed. Recover only
  # that exact platform dialog, once, by choosing Wait. Never dismiss a Cortex
  # ANR or treat an arbitrary Android dialog as product success.
  if (( systemui_wait_used == 0 )) &&
     test -s "$UI_DUMP" &&
     grep -Fq "System UI isn't responding" "$UI_DUMP"; then
    coords="$(python3 - "$UI_DUMP" <<'PY'
import re, sys
text=open(sys.argv[1], encoding="utf-8", errors="replace").read()
m=re.search(r'resource-id="android:id/aerr_wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text)
if m:
    x1,y1,x2,y2=map(int,m.groups())
    print((x1+x2)//2, (y1+y2)//2)
PY
)"
    read -r wait_x wait_y <<<"$coords"
    if [[ "$wait_x" =~ ^[0-9]+$ && "$wait_y" =~ ^[0-9]+$ ]]; then
      {
        echo
        echo "===== targeted API 26 System UI ANR recovery ====="
        echo "Choosing Wait at $wait_x,$wait_y; Cortex is not force-stopped."
        cat "$UI_DUMP"
      } >>"$DIAGNOSTICS"
      adb shell input tap "$wait_x" "$wait_y"
      systemui_wait_used=1
      wait_for_android
      wake_and_unlock
      adb shell am start -n com.night.cortex/.MainActivity >/dev/null
      continue
    fi
  fi

  # Any Cortex ANR is a product failure, not an emulator condition to hide.
  if test -s "$UI_DUMP" &&
     grep -q 'package="android"' "$UI_DUMP" &&
     grep -Fq "Cortex isn't responding" "$UI_DUMP"; then
    echo "Cortex ANR dialog detected during API 26 visual acceptance." >&2
    cat "$UI_DUMP" >&2
    exit 1
  fi

  sleep 1
done
grep -q 'package="com.night.cortex"' "$UI_DUMP"
grep -Eq 'text="Cortex"|content-desc="Cortex"' "$UI_DUMP"
grep -Eq 'text="Console"|content-desc="Console"' "$UI_DUMP"

adb exec-out screencap -p >"$SCREENSHOT"
test -s "$SCREENSHOT"
validate_png

# Prove the real connection bottom sheet outside Compose instrumentation.
# The test above validates the app's Compose surface; this path now exercises
# exactly what a user taps on the device and captures the actual framebuffer.
for i in $(seq 1 12); do
  adb shell rm -f /sdcard/cortex-api26-ui.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump --compressed /sdcard/cortex-api26-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/cortex-api26-ui.xml >"$UI_DUMP" 2>/dev/null || true
  coords="$(python3 - "$UI_DUMP" <<'PY'
import re, sys
text=open(sys.argv[1], encoding='utf-8', errors='replace').read()
matches=list(re.finditer(r'<node[^>]*(?:text="Connect"|content-desc="Connect")[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text))
if not matches:
    matches=list(re.finditer(r'<node[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*(?:text="Connect"|content-desc="Connect")', text))
if matches:
    x1,y1,x2,y2=map(int,matches[-1].groups())
    print((x1+x2)//2, (y1+y2)//2)
PY
)"
  read -r connect_x connect_y <<<"$coords"
  if [[ "$connect_x" =~ ^[0-9]+$ && "$connect_y" =~ ^[0-9]+$ ]]; then
    adb shell input tap "$connect_x" "$connect_y"
    break
  fi
  sleep 1
done

sheet_ready=0
for i in $(seq 1 20); do
  adb shell rm -f /sdcard/cortex-api26-ui.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump --compressed /sdcard/cortex-api26-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/cortex-api26-ui.xml >"$UI_DUMP" 2>/dev/null || true
  if test -s "$UI_DUMP" &&
     grep -Eq 'text="(Cortex Agent|Server connection)"|content-desc="(Cortex Agent|Server connection)"' "$UI_DUMP" &&
     grep -Eq 'text="(HTTPS Agent URL|Server URL)"|content-desc="(HTTPS Agent URL|Server URL)"' "$UI_DUMP" &&
     grep -Eq 'text="(Agent token|Access token)"|content-desc="(Agent token|Access token)"' "$UI_DUMP" &&
     grep -Eq 'text="Save connection"|content-desc="Save connection"' "$UI_DUMP"; then
    sheet_ready=1
    break
  fi
  sleep 1
done

if (( sheet_ready == 0 )); then
  echo "Cortex connection sheet did not become visible through the device UI hierarchy." >&2
  cat "$UI_DUMP" >&2 || true
  exit 1
fi

adb exec-out screencap -p >"$CONNECTION_SETUP_SCREENSHOT"
test -s "$CONNECTION_SETUP_SCREENSHOT"
HOME_SCREENSHOT="$SCREENSHOT"
HOME_SANITY="$SANITY"
SCREENSHOT="$CONNECTION_SETUP_SCREENSHOT"
SANITY="$GITHUB_WORKSPACE/cortex-connection-setup-sanity.txt"
validate_png
SCREENSHOT="$HOME_SCREENSHOT"
SANITY="$HOME_SANITY"
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true

echo "Cortex API 26 runtime, home visual, and connection-sheet visual acceptance passed."
