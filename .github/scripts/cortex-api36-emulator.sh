#!/usr/bin/env bash
set -Eeuo pipefail

APP_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$GITHUB_WORKSPACE/cortex-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
INSTRUMENTATION="$GITHUB_WORKSPACE/cortex-android-instrumentation.txt"
LOGCAT="$GITHUB_WORKSPACE/cortex-android-logcat.txt"
SCREENSHOT="$GITHUB_WORKSPACE/cortex-home-emulator.png"

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

  wait_for_android
  adb_cmd logcat -c >/dev/null 2>&1 || true

  set +e
  timeout 10m "${ADB[@]}" shell am instrument -w -r \
    -e class "$class_name" \
    com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner \
    >"$output_file" 2>&1
  local rc=$?
  set -e

  # Always collect crash evidence before making a pass/fail decision.
  adb_cmd logcat -d -v threadtime >"$LOGCAT" 2>&1 || true

  if (( rc != 0 )) || grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|shortMsg=' "$output_file"; then
    echo "$label instrumentation failed; allowing one bounded recovery retry."
    cat "$output_file"

    if grep -Eqi 'device offline|no devices|device.*not found|unable to connect to adb daemon|cannot connect to daemon|closed|transport error|protocol fault|can.t find service: (package|activity|settings)' "$output_file"; then
      recover_transport
    fi
    wait_for_android
    quiesce_android
    adb_cmd logcat -c >/dev/null 2>&1 || true

    set +e
    timeout 10m "${ADB[@]}" shell am instrument -w -r \
      -e class "$class_name" \
      com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner \
      >"$output_file" 2>&1
    rc=$?
    set -e
    adb_cmd logcat -d -v threadtime >"$LOGCAT" 2>&1 || true
  fi

  cat "$output_file"
  if (( rc != 0 )); then
    echo "$label instrumentation command failed with exit code $rc."
    return "$rc"
  fi
  if grep -Eqi 'Process crashed|INSTRUMENTATION_FAILED|shortMsg=' "$output_file"; then
    echo "$label instrumentation process crashed."
    return 1
  fi
  grep -q '^OK (' "$output_file"
}

SMOKE_OUT="$GITHUB_WORKSPACE/cortex-api36-smoke.txt"
PAIRING_OUT="$GITHUB_WORKSPACE/cortex-api36-pairing.txt"

run_test_class "com.night.cortex.CortexSmokeTest" "$SMOKE_OUT" "CortexSmokeTest"
run_test_class "com.night.cortex.CortexPairingScreenTest" "$PAIRING_OUT" "CortexPairingScreenTest"

cat "$SMOKE_OUT" "$PAIRING_OUT" >"$INSTRUMENTATION"

# Configure only this disposable emulator with an unreachable HTTPS Agent.
# This makes the real app render its current control surfaces without embedding
# production credentials or changing application behavior.
VISUAL_SETUP_OUT="$GITHUB_WORKSPACE/cortex-api36-visual-setup.txt"
run_test_class "com.night.cortex.CortexVisualEvidenceSetupTest" "$VISUAL_SETUP_OUT" "CortexVisualEvidenceSetupTest"

wait_for_android
adb_cmd logcat -d -v threadtime >"$LOGCAT" 2>&1 || true
adb_retry "Force-stop Cortex" shell am force-stop com.night.cortex || true
adb_retry "Launch Cortex" shell am start -W -n com.night.cortex/.MainActivity
sleep 3
wait_for_android

EVIDENCE_DIR="$GITHUB_WORKSPACE/cortex-api36-screens"
mkdir -p "$EVIDENCE_DIR"

capture_screen() {
  local name="$1"
  wait_for_android
  adb_cmd exec-out screencap -p >"$EVIDENCE_DIR/$name.png"
  test -s "$EVIDENCE_DIR/$name.png"
}

tap_tab() {
  local label="$1"
  local attempt xml host_xml coords
  host_xml="$RUNNER_TEMP/cortex-window.xml"
  for attempt in 1 2 3 4 5 6; do
    adb_cmd shell uiautomator dump /sdcard/cortex-window.xml >/dev/null
    adb_cmd pull /sdcard/cortex-window.xml "$host_xml" >/dev/null
    coords="$(python3 - "$host_xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
label = sys.argv[2]
for node in root.iter("node"):
    if node.attrib.get("text") == label:
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print(f"{(x1+x2)//2} {(y1+y2)//2}")
            raise SystemExit
PY
)"
    if [[ -n "$coords" ]]; then
      read -r x y <<<"$coords"
      adb_cmd shell input tap "$x" "$y"
      sleep 2
      return 0
    fi
    # Tabs are a horizontal scroll row; move it without fixed target coordinates.
    adb_cmd shell input swipe 900 180 180 180 350
    sleep 1
  done
  echo "Could not locate Cortex tab '$label' by accessibility text."
  return 1
}

capture_screen "01-console"
for entry in "Pairing:02-pairing" "Files:03-files" "Backups:04-backups" "Startup:05-startup" "Settings:06-settings" "Activity:07-activity"; do
  label="${entry%%:*}"
  name="${entry#*:}"
  tap_tab "$label"
  capture_screen "$name"
done

cp "$EVIDENCE_DIR/01-console.png" "$SCREENSHOT"
test -s "$SCREENSHOT"

echo "Cortex API 36 instrumentation and multi-surface screenshot capture passed."
