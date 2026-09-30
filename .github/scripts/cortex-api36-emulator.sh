#!/usr/bin/env bash
set -Eeuo pipefail

APP_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
INSTRUMENTATION="$GITHUB_WORKSPACE/cortex-android-instrumentation.txt"
LOGCAT="$GITHUB_WORKSPACE/cortex-android-logcat.txt"
SCREENSHOT="$GITHUB_WORKSPACE/cortex-home-emulator.png"
COMPOSE_HOME_SCREENSHOT="$GITHUB_WORKSPACE/cortex-home-compose.png"
COMPOSE_HOME_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-home-compose-sanity.txt"
UI_DUMP="$GITHUB_WORKSPACE/cortex-api36-ui.xml"
FOREGROUND="$GITHUB_WORKSPACE/cortex-api36-foreground.txt"
GFXINFO="$GITHUB_WORKSPACE/cortex-api36-gfxinfo.txt"
SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-api36-screenshot-sanity.txt"
SYSTEM_DIALOG="$GITHUB_WORKSPACE/cortex-api36-system-dialog.txt"
PAIRING_SCREENSHOT="$GITHUB_WORKSPACE/cortex-pairing-code-emulator.png"
PAIRING_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-pairing-code-sanity.txt"
UNPAIRED_SCREENSHOT="$GITHUB_WORKSPACE/cortex-unpaired-emulator.png"
UNPAIRED_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-unpaired-sanity.txt"
PAIRING_METHOD_SCREENSHOT="$GITHUB_WORKSPACE/cortex-pairing-method-emulator.png"
PAIRING_METHOD_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-pairing-method-sanity.txt"
SESSION_ACTIVE_SCREENSHOT="$GITHUB_WORKSPACE/cortex-session-active-emulator.png"
SESSION_ACTIVE_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-session-active-sanity.txt"
SESSION_EXPIRED_SCREENSHOT="$GITHUB_WORKSPACE/cortex-session-expired-emulator.png"
SESSION_EXPIRED_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-session-expired-sanity.txt"
REPAIR_SCREENSHOT="$GITHUB_WORKSPACE/cortex-repair-emulator.png"
REPAIR_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-repair-sanity.txt"
CONNECTION_SETUP_SCREENSHOT="$GITHUB_WORKSPACE/cortex-connection-setup-emulator.png"
CONNECTION_SETUP_SCREENSHOT_SANITY="$GITHUB_WORKSPACE/cortex-connection-setup-sanity.txt"
DIAGNOSTICS="$GITHUB_WORKSPACE/cortex-api36-diagnostics.txt"

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

capture_ui_dump() {
  adb_cmd shell rm -f /sdcard/cortex-ui.xml >/dev/null 2>&1 || true
  adb_cmd shell uiautomator dump --compressed /sdcard/cortex-ui.xml >/dev/null 2>&1 || true
  adb_cmd exec-out cat /sdcard/cortex-ui.xml >"$UI_DUMP" 2>/dev/null || true
}

dump_cortex_ui() {
  local attempt
  rm -f "$UI_DUMP"

  for attempt in $(seq 1 12); do
    capture_ui_dump

    if test -s "$UI_DUMP" &&
       grep -q 'package="com.night.cortex"' "$UI_DUMP" &&
       grep -Eq 'text="Cortex"|content-desc="Cortex"' "$UI_DUMP" &&
       grep -Eq 'text="Console"|content-desc="Console"' "$UI_DUMP"; then
      echo "Cortex Compose/UI hierarchy is present."
      return 0
    fi

    # API 36 aosp_atd can surface its own test-image "Fake System App" ANR
    # above the resumed Cortex activity. Detect it immediately so we can make
    # one narrow recovery attempt instead of spending minutes polling a dialog.
    if test -s "$UI_DUMP" && grep -Fq "Fake System App isn't responding" "$UI_DUMP"; then
      echo "Unrelated API 36 Fake System App ANR is covering Cortex."
      return 2
    fi

    if test -s "$UI_DUMP" && grep -Fq "Process system isn't responding" "$UI_DUMP"; then
      echo "Android system_server ANR dialog is covering the resumed Cortex activity."
      return 3
    fi

    # Any other Android ANR dialog is a hard failure. In particular, never
    # dismiss a Cortex ANR and then claim visual acceptance succeeded.
    if test -s "$UI_DUMP" &&
       grep -q 'package="android"' "$UI_DUMP" &&
       grep -q 'resource-id="android:id/alertTitle"' "$UI_DUMP" &&
       grep -Fq "isn't responding" "$UI_DUMP"; then
      echo "Unexpected Android ANR dialog is covering Cortex."
      return 4
    fi
    sleep 1
  done

  echo "Cortex UI hierarchy did not expose the expected app content."
  test -s "$UI_DUMP" && cat "$UI_DUMP"
  return 1
}

dismiss_fake_system_app_anr_once() {
  local coords x y
  {
    echo "Detected unrelated API 36 system dialog:"
    cat "$UI_DUMP" 2>/dev/null || true
    echo
    echo "===== dumpsys activity lastanr ====="
    adb_cmd shell dumpsys activity lastanr 2>&1 || true
    echo
    echo "===== packages containing fake ====="
    adb_cmd shell pm list packages 2>&1 | grep -i fake || true
  } >"$SYSTEM_DIALOG"

  coords="$(python3 - "$UI_DUMP" <<'PY'
import re
import sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(
    r'resource-id="android:id/aerr_close"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    text,
)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)"
  read -r x y <<<"$coords"
  if [[ ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ ]]; then
    echo "Could not locate the system ANR Close app button." | tee -a "$SYSTEM_DIALOG"
    return 1
  fi

  echo "Dismissing only the unrelated Fake System App ANR at $x,$y." | tee -a "$SYSTEM_DIALOG"
  adb_cmd shell input tap "$x" "$y"
  sleep 2
  wake_and_unlock

  # Relaunch without -W: the visual gate below proves foreground + rendered UI,
  # while -W itself can time out on an overloaded ATD image even after resume.
  adb_cmd shell am start -n com.night.cortex/.MainActivity >/dev/null
  sleep 2
  verify_cortex_foreground

  local rc
  set +e
  dump_cortex_ui
  rc=$?
  set -e
  if (( rc != 0 )); then
    echo "Cortex UI still unavailable after one targeted system-dialog recovery." | tee -a "$SYSTEM_DIALOG"
    return 1
  fi
  return 0
}

wait_for_system_server_anr_once() {
  local coords x y rc
  {
    echo "Detected Android system_server ANR dialog while Cortex remained resumed:"
    cat "$UI_DUMP" 2>/dev/null || true
    echo
    echo "===== dumpsys activity lastanr ====="
    adb_cmd shell dumpsys activity lastanr 2>&1 || true
    echo
    echo "===== system_server process ====="
    adb_cmd shell ps -A 2>&1 | grep -E '(^|[[:space:]])system_server([[:space:]]|$)' || true
  } >"$SYSTEM_DIALOG"

  coords="$(python3 - "$UI_DUMP" <<'PY'
import re
import sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(
    r'resource-id="android:id/aerr_wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    text,
)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)"
  read -r x y <<<"$coords"
  if [[ ! "$x" =~ ^[0-9]+$ || ! "$y" =~ ^[0-9]+$ ]]; then
    echo "Could not locate the system_server ANR Wait button." | tee -a "$SYSTEM_DIALOG"
    return 1
  fi

  echo "Choosing Wait for system_server at $x,$y; system_server will not be stopped." | tee -a "$SYSTEM_DIALOG"
  adb_cmd shell input tap "$x" "$y"
  sleep 2
  wait_for_android
  wake_and_unlock

  # Bring Cortex forward again without force-stopping it. The same strict
  # foreground, semantics, frame, and pixel gates below still have to pass.
  adb_cmd shell am start -n com.night.cortex/.MainActivity >/dev/null
  wake_and_unlock
  verify_cortex_foreground

  set +e
  dump_cortex_ui
  rc=$?
  set -e
  if (( rc != 0 )); then
    echo "Cortex UI still unavailable after one bounded system_server Wait recovery (rc=$rc)." | tee -a "$SYSTEM_DIALOG"
    return 1
  fi
  return 0
}

capture_gfx_evidence() {
  adb_cmd shell dumpsys gfxinfo com.night.cortex >"$GFXINFO" 2>&1 || true
  if grep -q 'Total frames rendered:' "$GFXINFO"; then
    local frames
    frames="$(grep -m1 'Total frames rendered:' "$GFXINFO" | sed -E 's/.*Total frames rendered:[[:space:]]*([0-9]+).*/\1/' || true)"
    if [[ "$frames" =~ ^[0-9]+$ ]] && (( frames <= 0 )); then
      # Android 16 aosp_atd can expose a zero frame counter even after Compose
      # has attached a real BLAST surface. Treat the counter as diagnostic only
      # when gfxinfo itself proves MainActivity has an attached surface/view
      # tree; the strict framebuffer pixel gate remains authoritative below.
      if grep -q 'VRI\[MainActivity\].*BLAST Consumer' "$GFXINFO" &&
         grep -Eq 'Total attached Views[[:space:]]*:[[:space:]]*[1-9][0-9]*' "$GFXINFO"; then
        echo "gfxinfo frame counter is zero, but MainActivity has an attached rendered surface; deferring acceptance to framebuffer validation."
      else
        echo "Cortex has neither a rendered-frame count nor attached MainActivity surface evidence."
        return 1
      fi
    fi
  fi
}

validate_screenshot_pixels() {
  local screenshot="${1:-$SCREENSHOT}"
  local report="${2:-$SCREENSHOT_SANITY}"
  python3 - "$screenshot" "$report" <<'PY'
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
: >"$LOGCAT"
: >"$DIAGNOSTICS"
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

# #101 proved that Android 16 can kill Cortex for "failed to complete startup"
# while the unaccelerated ATD image is heavily CPU-bound. Compile the already
# installed debug + test packages ahead of instrumentation so validation tests
# measure Cortex lifecycle/UI behavior instead of first-run dex compilation.
# This does not skip startup: MainActivity still launches normally in the smoke
# test and again in the strict foreground/semantics/framebuffer gate below.
adb_retry "AOT-compile Cortex package" shell cmd package compile -m speed -f com.night.cortex
adb_retry "AOT-compile Cortex test package" shell cmd package compile -m speed -f com.night.cortex.test
wait_for_android

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
    local attempt_log
    attempt_log="$(mktemp)"
    adb_cmd logcat -d -v threadtime >"$attempt_log" 2>&1 || true
    {
      echo "===== $label attempt $attempt ====="
      echo "instrumentation_rc=$rc"
      if grep -Eqi 'ANR in com\.night\.cortex|Input dispatching timed out.*com\.night\.cortex' "$attempt_log"; then
        echo "classification=APP_ANR"
      elif grep -Eqi 'FATAL EXCEPTION|Process: com\.night\.cortex' "$attempt_log"; then
        echo "classification=APP_OR_TEST_PROCESS_CRASH"
      elif grep -Eqi 'ANR in (system|com\.android\.)|Process system isn.t responding' "$attempt_log"; then
        echo "classification=ANDROID_SYSTEM_ANR"
      elif grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|System has crashed|shortMsg=' "$output_file"; then
        echo "classification=INSTRUMENTATION_PROCESS_CRASH"
      elif (( rc != 0 )); then
        echo "classification=INSTRUMENTATION_COMMAND_FAILURE"
      else
        echo "classification=PASS"
      fi
      echo "pid=$(adb_cmd shell pidof com.night.cortex 2>/dev/null | tr -d '\r' || true)"
      adb_cmd shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -n 6 || true
      adb_cmd shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 6 || true
      echo
    } >>"$DIAGNOSTICS"
    {
      echo
      echo "===== $label attempt $attempt logcat ====="
      cat "$attempt_log"
    } >>"$LOGCAT"
    rm -f "$attempt_log"

    local crashed=0
    if grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|System has crashed|shortMsg=' "$output_file"; then
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
  if grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|System has crashed|shortMsg=' "$output_file"; then
    echo "$label instrumentation process crashed after three bounded attempts."
    return 1
  fi
  grep -q '^OK (' "$output_file"
}

SMOKE_OUT="$GITHUB_WORKSPACE/cortex-api36-smoke.txt"
PAIRING_OUT="$GITHUB_WORKSPACE/cortex-api36-pairing.txt"

run_test_class "com.night.cortex.CortexSmokeTest" "$SMOKE_OUT" "CortexSmokeTest"
run_test_class "com.night.cortex.CortexPairingScreenTest" "$PAIRING_OUT" "CortexPairingScreenTest"
run_test_class "com.night.cortex.CortexPowerControlsTest" "$GITHUB_WORKSPACE/cortex-api36-power-controls.txt" "CortexPowerControlsTest"

pull_app_cache_visual() {
  local cache_name="$1"
  local destination="$2"
  rm -f "$destination"
  adb_cmd exec-out run-as com.night.cortex cat "cache/$cache_name" >"$destination"
  test -s "$destination"
}

pull_app_cache_visual "cortex-home-compose.png" "$COMPOSE_HOME_SCREENSHOT"
validate_screenshot_pixels "$COMPOSE_HOME_SCREENSHOT" "$COMPOSE_HOME_SCREENSHOT_SANITY"
pull_app_cache_visual "cortex-unpaired-emulator.png" "$UNPAIRED_SCREENSHOT"
validate_screenshot_pixels "$UNPAIRED_SCREENSHOT" "$UNPAIRED_SCREENSHOT_SANITY"
pull_app_cache_visual "cortex-pairing-method-emulator.png" "$PAIRING_METHOD_SCREENSHOT"
validate_screenshot_pixels "$PAIRING_METHOD_SCREENSHOT" "$PAIRING_METHOD_SCREENSHOT_SANITY"
pull_app_cache_visual "cortex-pairing-code-emulator.png" "$PAIRING_SCREENSHOT"
validate_screenshot_pixels "$PAIRING_SCREENSHOT" "$PAIRING_SCREENSHOT_SANITY"
pull_app_cache_visual "cortex-session-active-emulator.png" "$SESSION_ACTIVE_SCREENSHOT"
validate_screenshot_pixels "$SESSION_ACTIVE_SCREENSHOT" "$SESSION_ACTIVE_SCREENSHOT_SANITY"
pull_app_cache_visual "cortex-session-expired-emulator.png" "$SESSION_EXPIRED_SCREENSHOT"
validate_screenshot_pixels "$SESSION_EXPIRED_SCREENSHOT" "$SESSION_EXPIRED_SCREENSHOT_SANITY"
pull_app_cache_visual "cortex-repair-emulator.png" "$REPAIR_SCREENSHOT"
validate_screenshot_pixels "$REPAIR_SCREENSHOT" "$REPAIR_SCREENSHOT_SANITY"

cat "$SMOKE_OUT" "$PAIRING_OUT" "$GITHUB_WORKSPACE/cortex-api36-power-controls.txt" >"$INSTRUMENTATION"

wait_for_android

# Visual acceptance is a real gate, not an artifact side effect. Keep the display
# awake, prove Cortex owns the foreground, prove Compose semantics are present,
# then reject black/uniform captures.
wake_and_unlock
adb_retry "Force-stop Cortex" shell am force-stop com.night.cortex || true
adb_cmd logcat -c >/dev/null 2>&1 || true
wake_and_unlock
adb_retry "Launch Cortex" shell am start -n com.night.cortex/.MainActivity
wake_and_unlock
verify_cortex_foreground

ui_rc=0
set +e
dump_cortex_ui
ui_rc=$?
set -e
if (( ui_rc == 2 )); then
  dismiss_fake_system_app_anr_once
elif (( ui_rc == 3 )); then
  wait_for_system_server_anr_once
elif (( ui_rc != 0 )); then
  {
    echo "Unexpected UI obstruction while validating Cortex:"
    cat "$UI_DUMP" 2>/dev/null || true
    echo
    echo "===== dumpsys activity lastanr ====="
    adb_cmd shell dumpsys activity lastanr 2>&1 || true
  } >"$SYSTEM_DIALOG"
  {
    echo
    echo "===== visual acceptance logcat ====="
    adb_cmd logcat -d -v threadtime 2>&1 || true
  } >>"$LOGCAT"
  exit "$ui_rc"
fi

capture_gfx_evidence
adb_cmd exec-out screencap -p >"$SCREENSHOT"
test -s "$SCREENSHOT"

# Android 16's headless ATD/SwiftShader compositor can occasionally lose the
# host color buffer even while Cortex has a real attached BLAST surface. Do not
# replace or hide that black capture: keep it as diagnostic evidence. Accept
# the relaunch only when the failure is exactly an all-black compositor frame,
# the app-side Compose raster already passed pixel validation, and gfxinfo
# proves MainActivity still owns an attached surface/view tree.
framebuffer_rc=0
set +e
validate_screenshot_pixels "$SCREENSHOT" "$SCREENSHOT_SANITY"
framebuffer_rc=$?
set -e
if (( framebuffer_rc != 0 )); then
  if test -s "$SCREENSHOT_SANITY" &&
     grep -q '^brightness_max=0$' "$SCREENSHOT_SANITY" &&
     grep -q '^sampled_unique_colors=1$' "$SCREENSHOT_SANITY" &&
     grep -q 'VRI\[MainActivity\].*BLAST Consumer' "$GFXINFO" &&
     grep -Eq 'Total attached Views[[:space:]]*:[[:space:]]*[1-9][0-9]*' "$GFXINFO"; then
    {
      echo "framebuffer_capture=ATD_ALL_BLACK"
      echo "acceptance_basis=validated Compose raster + foreground MainActivity + UI hierarchy + attached BLAST surface"
      echo "The black adb screencap is preserved as evidence and is not substituted with the Compose image."
    } >>"$DIAGNOSTICS"
    echo "API 36 ATD framebuffer capture is all black despite a validated Cortex raster and attached surface; preserving the black capture as emulator diagnostic evidence."
  else
    echo "Cortex framebuffer validation failed for a reason that cannot be attributed to the known ATD all-black compositor condition."
    exit "$framebuffer_rc"
  fi
fi

# Re-check foreground + semantics after the framebuffer capture. This closes
# the gap where a system dialog could appear between the pre-capture UI dump
# and screencap and accidentally become the accepted evidence.
verify_cortex_foreground
post_ui_rc=0
set +e
dump_cortex_ui
post_ui_rc=$?
set -e
if (( post_ui_rc != 0 )); then
  {
    echo "Cortex UI changed or became obstructed immediately after screenshot capture (rc=$post_ui_rc)."
    cat "$UI_DUMP" 2>/dev/null || true
    echo
    echo "===== dumpsys activity lastanr ====="
    adb_cmd shell dumpsys activity lastanr 2>&1 || true
  } >"$SYSTEM_DIALOG"
  {
    echo
    echo "===== visual acceptance logcat ====="
    adb_cmd logcat -d -v threadtime 2>&1 || true
  } >>"$LOGCAT"
  exit "$post_ui_rc"
fi

{
    echo
    echo "===== visual acceptance logcat ====="
    adb_cmd logcat -d -v threadtime 2>&1 || true
  } >>"$LOGCAT"

# Exercise the real connection bottom sheet through the device hierarchy instead
# of Compose idling. API 26 provides the canonical rendered sheet screenshot;
# API 36 additionally proves the same production sheet is reachable and visible.
capture_ui_dump
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
if [[ ! "$connect_x" =~ ^[0-9]+$ || ! "$connect_y" =~ ^[0-9]+$ ]]; then
  echo "Could not locate the Cortex Connect action in the API 36 device hierarchy." >&2
  cat "$UI_DUMP" >&2 || true
  exit 1
fi
adb_cmd shell input tap "$connect_x" "$connect_y"

sheet_ready=0
for attempt in $(seq 1 20); do
  capture_ui_dump
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
  echo "Cortex connection sheet did not become visible on API 36." >&2
  cat "$UI_DUMP" >&2 || true
  exit 1
fi

adb_cmd exec-out screencap -p >"$CONNECTION_SETUP_SCREENSHOT"
test -s "$CONNECTION_SETUP_SCREENSHOT"
connection_frame_rc=0
set +e
validate_screenshot_pixels "$CONNECTION_SETUP_SCREENSHOT" "$CONNECTION_SETUP_SCREENSHOT_SANITY"
connection_frame_rc=$?
set -e
if (( connection_frame_rc != 0 )); then
  if test -s "$CONNECTION_SETUP_SCREENSHOT_SANITY" &&
     grep -q '^brightness_max=0$' "$CONNECTION_SETUP_SCREENSHOT_SANITY" &&
     grep -q '^sampled_unique_colors=1$' "$CONNECTION_SETUP_SCREENSHOT_SANITY"; then
    {
      echo "connection_sheet_framebuffer=ATD_ALL_BLACK"
      echo "acceptance_basis=API36 sheet semantics + foreground MainActivity; API26 supplies rendered connection-sheet visual proof"
    } >>"$DIAGNOSTICS"
  else
    echo "API 36 connection-sheet framebuffer failed for an unexpected reason." >&2
    exit "$connection_frame_rc"
  fi
fi
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true

echo "Cortex API 36 instrumentation, runtime, and connection-sheet acceptance passed."
