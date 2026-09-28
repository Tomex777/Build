#!/usr/bin/env bash
set -Eeuo pipefail

APP_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
INSTRUMENTATION="$GITHUB_WORKSPACE/cortex-android-instrumentation.txt"
LOGCAT="$GITHUB_WORKSPACE/cortex-android-logcat.txt"
SCREENSHOT="$GITHUB_WORKSPACE/cortex-home-emulator.png"
UI_DUMP="$GITHUB_WORKSPACE/cortex-api36-ui.xml"
FOREGROUND="$GITHUB_WORKSPACE/cortex-api36-foreground.txt"
GFXINFO="$GITHUB_WORKSPACE/cortex-api36-gfxinfo.txt"
SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-api36-screenshot-sanity.txt"

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB+=(-s "$ANDROID_SERIAL")
fi

adb_cmd() {
  "${ADB[@]}" "$@"
}

transport_ready() {
  local state
  state="$(adb_cmd get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || return 1
  adb_cmd shell true >/dev/null 2>&1
}

framework_probe() {
  local boot
  transport_ready || return 1
  boot="$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$boot" == "1" ]] || return 1
  adb_cmd shell pm path android 2>/dev/null | grep -q '^package:' || return 1
  adb_cmd shell am get-current-user >/dev/null 2>&1 || return 1
  adb_cmd shell settings get global device_provisioned >/dev/null 2>&1 || return 1
}

recover_transport() {
  echo "Recovering adb transport without disturbing a healthy Android framework..."
  adb start-server >/dev/null 2>&1 || true
  adb reconnect offline >/dev/null 2>&1 || true
  sleep 2
}

wait_for_android() {
  local attempt consecutive=0
  for attempt in $(seq 1 90); do
    if framework_probe; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= 3 )); then
        echo "Android framework is stable (3 consecutive probes; attempt $attempt/90)."
        return 0
      fi
    else
      consecutive=0
      if ! transport_ready; then
        recover_transport
      fi
    fi

    if (( attempt % 15 == 0 )); then
      echo "Waiting for package/activity/settings services ($attempt/90)..."
      adb devices -l || true
      adb_cmd shell getprop sys.boot_completed 2>/dev/null || true
      adb_cmd shell service check package 2>/dev/null || true
      adb_cmd shell service check activity 2>/dev/null || true
    fi
    sleep 2
  done

  echo "Android framework did not become stable within three minutes."
  adb devices -l || true
  return 1
}

quiesce_android() {
  echo "Allowing the API 36 framework to settle before instrumentation..."
  sleep 15
  wait_for_android
}

quiesce_android_retry() {
  echo "Allowing Android 16 post-boot services to settle before the one retry..."
  sleep 35
  wait_for_android
}

adb_retry() {
  local description="$1"
  shift
  local attempt
  for attempt in 1 2 3; do
    wait_for_android
    if adb_cmd "$@"; then
      return 0
    fi

    echo "$description failed on attempt $attempt/3."
    if ! transport_ready; then
      recover_transport
    else
      echo "adb transport is alive; waiting for Android framework services instead of restarting adb."
      sleep 3
    fi
  done

  echo "$description failed after three bounded attempts."
  return 1
}

wake_and_unlock() {
  # Instrumentation can run long enough for a headless emulator display to sleep.
  # A sleeping display produces a technically valid but completely black screenshot.
  adb_cmd shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
  adb_cmd shell svc power stayon true >/dev/null 2>&1 || true
  adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb_cmd shell input keyevent 82 >/dev/null 2>&1 || true
}

verify_cortex_foreground() {
  local attempt activity focus
  : >"$FOREGROUND"

  for attempt in $(seq 1 20); do
    test -n "$(adb_cmd shell pidof com.night.cortex 2>/dev/null | tr -d '\r' || true)" || {
      echo "Cortex process is not running." >>"$FOREGROUND"
      sleep 1
      continue
    }

    activity="$(adb_cmd shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -n 4 || true)"
    focus="$(adb_cmd shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4 || true)"
    {
      echo "attempt=$attempt"
      echo "pid=$(adb_cmd shell pidof com.night.cortex 2>/dev/null | tr -d '\r' || true)"
      echo "$activity"
      echo "$focus"
    } >"$FOREGROUND"

    if printf '%s\n%s\n' "$activity" "$focus" | grep -q 'com.night.cortex/.MainActivity'; then
      echo "Cortex MainActivity is foreground."
      return 0
    fi
    sleep 1
  done

  echo "Cortex MainActivity never became the foreground activity."
  cat "$FOREGROUND"
  return 1
}

dump_cortex_ui() {
  local attempt
  rm -f "$UI_DUMP"

  for attempt in $(seq 1 15); do
    adb_cmd shell rm -f /sdcard/cortex-ui.xml >/dev/null 2>&1 || true
    adb_cmd shell uiautomator dump --compressed /sdcard/cortex-ui.xml >/dev/null 2>&1 || true
    adb_cmd exec-out cat /sdcard/cortex-ui.xml >"$UI_DUMP" 2>/dev/null || true

    if test -s "$UI_DUMP" &&
       grep -q 'package="com.night.cortex"' "$UI_DUMP" &&
       grep -Eq 'text="Cortex"|content-desc="Cortex"' "$UI_DUMP" &&
       grep -Eq 'text="Console"|content-desc="Console"' "$UI_DUMP"; then
      echo "Cortex Compose/UI hierarchy is present."
      return 0
    fi
    sleep 1
  done

  echo "Cortex UI hierarchy did not expose the expected app content."
  test -s "$UI_DUMP" && cat "$UI_DUMP"
  return 1
}

capture_gfx_evidence() {
  adb_cmd shell dumpsys gfxinfo com.night.cortex >"$GFXINFO" 2>&1 || true
  if grep -q 'Total frames rendered:' "$GFXINFO"; then
    local frames
    frames="$(grep -m1 'Total frames rendered:' "$GFXINFO" | sed -E 's/.*Total frames rendered:[[:space:]]*([0-9]+).*/\1/' || true)"
    if [[ "$frames" =~ ^[0-9]+$ ]] && (( frames <= 0 )); then
      echo "Cortex reported zero rendered frames."
      return 1
    fi
  fi
}

validate_screenshot_pixels() {
  python3 - "$SCREENSHOT" "$SCREENSHOT_SANITY" <<'PY'
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
    raise SystemExit(f"Unsupported screenshot PNG format: {width}x{height} depth={bit_depth} interlace={interlace}")

channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
if channels is None:
    raise SystemExit(f"Unsupported screenshot PNG color type: {color_type}")

raw = zlib.decompress(bytes(compressed))
stride = width * channels
expected = height * (stride + 1)
if len(raw) != expected:
    raise SystemExit(f"Unexpected screenshot payload size: {len(raw)} != {expected}")

rows = []
prior = bytearray(stride)
offset = 0

def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c

for _ in range(height):
    filt = raw[offset]
    offset += 1
    scan = bytearray(raw[offset:offset + stride])
    offset += stride
    recon = bytearray(stride)
    for i, value in enumerate(scan):
        left = recon[i - channels] if i >= channels else 0
        up = prior[i]
        up_left = prior[i - channels] if i >= channels else 0
        if filt == 0:
            out = value
        elif filt == 1:
            out = (value + left) & 255
        elif filt == 2:
            out = (value + up) & 255
        elif filt == 3:
            out = (value + ((left + up) // 2)) & 255
        elif filt == 4:
            out = (value + paeth(left, up, up_left)) & 255
        else:
            raise SystemExit(f"Unsupported PNG filter {filt}")
        recon[i] = out
    rows.append(recon)
    prior = recon

sample_step = max(1, (width * height) // 200000)
minimum = 255
maximum = 0
nonblack = 0
sampled = 0
unique = set()

for y, row in enumerate(rows):
    for x in range(width):
        index = x * channels
        if color_type == 0:
            rgb = (row[index],) * 3
        elif color_type == 2:
            rgb = tuple(row[index:index + 3])
        elif color_type == 4:
            rgb = (row[index],) * 3
        else:
            rgb = tuple(row[index:index + 3])

        brightness = max(rgb)
        minimum = min(minimum, brightness)
        maximum = max(maximum, brightness)
        if brightness > 12:
            nonblack += 1

        linear = y * width + x
        if linear % sample_step == 0:
            sampled += 1
            if len(unique) < 256:
                unique.add(rgb)

pixels = width * height
nonblack_fraction = nonblack / pixels
with open(report, "w", encoding="utf-8") as out:
    out.write(f"size={width}x{height}\n")
    out.write(f"brightness_min={minimum}\n")
    out.write(f"brightness_max={maximum}\n")
    out.write(f"nonblack_fraction={nonblack_fraction:.6f}\n")
    out.write(f"sampled_unique_colors={len(unique)}\n")

if maximum <= 12:
    raise SystemExit("Screenshot is effectively all black")
if maximum - minimum <= 6:
    raise SystemExit("Screenshot is effectively uniform")
if nonblack_fraction < 0.01:
    raise SystemExit(f"Screenshot has too little visible content ({nonblack_fraction:.4%} non-black)")
if len(unique) < 8:
    raise SystemExit(f"Screenshot has too little visual variation ({len(unique)} sampled colors)")

print(open(report, encoding="utf-8").read(), end="")
PY
}

echo "=== Cortex API 36 runtime validation ==="
wait_for_android
quiesce_android

# These settings reduce emulator install/test interference, but they are not
# themselves validation requirements. The APK install and instrumentation below are.
adb_retry "Disable package verifier" shell settings put global package_verifier_enable 0 || true
adb_retry "Disable adb install verifier" shell settings put global verifier_verify_adb_installs 0 || true

test -s "$APP_APK"
test -s "$TEST_APK"
adb_retry "Install Cortex APK" install -r -g "$APP_APK"
adb_retry "Install Cortex instrumentation APK" install -r -g "$TEST_APK"

run_test_class() {
  local class_name="$1"
  local output_file="$2"
  local label="$3"
  local attempt rc=1

  for attempt in 1 2 3; do
    wait_for_android
    if (( attempt > 1 )); then
      echo "$label startup crashed on the prior attempt; giving Android 16 extra time to settle before retry $attempt/3."
      sleep 35
      wait_for_android
    fi

    adb_cmd logcat -c >/dev/null 2>&1 || true

    set +e
    timeout 10m "${ADB[@]}" shell am instrument -w -r \
      -e class "$class_name" \
      com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner \
      >"$output_file" 2>&1
    rc=$?
    set -e

    # Always collect crash evidence before making a pass/fail decision.
    adb_cmd logcat -d -v threadtime >"$LOGCAT" 2>&1 || true

    local crashed=0
    if grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|shortMsg=' "$output_file"; then
      crashed=1
    fi

    if (( rc == 0 && crashed == 0 )); then
      break
    fi

    echo "$label instrumentation attempt $attempt/3 failed."
    cat "$output_file"

    # Preserve Android's ANR diagnosis in the same uploaded log artifact.
    {
      echo
      echo "===== dumpsys activity lastanr after $label attempt $attempt/3 ====="
      adb_cmd shell dumpsys activity lastanr 2>&1 || true
    } >>"$LOGCAT"

    if (( attempt >= 3 )); then
      break
    fi

    if grep -Eqi 'device offline|no devices|device.*not found|unable to connect to adb daemon|cannot connect to daemon|closed|transport error|protocol fault|can.t find service: (package|activity|settings)' "$output_file"; then
      recover_transport
    fi
    wait_for_android
  done

  cat "$output_file"
  if (( rc != 0 )); then
    echo "$label instrumentation command failed with exit code $rc."
    return "$rc"
  fi
  if grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|shortMsg=' "$output_file"; then
    echo "$label instrumentation process crashed after three bounded attempts."
    return 1
  fi
  grep -q '^OK (' "$output_file"
}

SMOKE_OUT="$GITHUB_WORKSPACE/cortex-api36-smoke.txt"
PAIRING_OUT="$GITHUB_WORKSPACE/cortex-api36-pairing.txt"

run_test_class "com.night.cortex.CortexSmokeTest" "$SMOKE_OUT" "CortexSmokeTest"
run_test_class "com.night.cortex.CortexPairingScreenTest" "$PAIRING_OUT" "CortexPairingScreenTest"

cat "$SMOKE_OUT" "$PAIRING_OUT" >"$INSTRUMENTATION"

wait_for_android
adb_cmd logcat -d -v threadtime >"$LOGCAT" 2>&1 || true

# Visual acceptance is a real gate, not an artifact side effect. Keep the display
# awake, prove Cortex owns the foreground, prove Compose semantics are present,
# then reject black/uniform captures.
wake_and_unlock
adb_retry "Force-stop Cortex" shell am force-stop com.night.cortex || true
wake_and_unlock
adb_retry "Launch Cortex" shell am start -W -n com.night.cortex/.MainActivity
wait_for_android
wake_and_unlock
verify_cortex_foreground
dump_cortex_ui
capture_gfx_evidence
adb_cmd exec-out screencap -p >"$SCREENSHOT"
test -s "$SCREENSHOT"
validate_screenshot_pixels

echo "Cortex API 36 instrumentation and visual acceptance passed."
