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

device_ready() {
  local state boot
  state="$(adb_cmd get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || return 1
  boot="$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$boot" == "1" ]] || return 1
  adb_cmd shell true >/dev/null 2>&1
}

wait_for_android() {
  local attempt
  for attempt in $(seq 1 30); do
    if device_ready; then
      echo "Android is healthy (attempt $attempt/30)."
      return 0
    fi
    if (( attempt % 10 == 0 )); then
      echo "Still waiting for a healthy Android device ($attempt/30)."
      adb devices -l || true
    fi
    sleep 2
  done
  echo "Android did not become healthy within 60 seconds."
  adb devices -l || true
  return 1
}

recover_adb() {
  echo "Recovering adb transport..."
  adb kill-server >/dev/null 2>&1 || true
  sleep 2
  adb start-server
  adb reconnect offline >/dev/null 2>&1 || true
  wait_for_android
}

require_android() {
  if device_ready; then
    return 0
  fi
  recover_adb
}

adb_retry() {
  local description="$1"
  shift
  local attempt
  for attempt in 1 2 3; do
    if require_android && adb_cmd "$@"; then
      return 0
    fi
    echo "$description failed on attempt $attempt/3."
    if (( attempt < 3 )); then
      recover_adb || true
    fi
  done
  echo "$description failed after three bounded attempts."
  return 1
}

echo "=== Cortex API 36 runtime validation ==="
require_android

adb_retry "Disable package verifier" shell settings put global package_verifier_enable 0 || true
adb_retry "Disable adb install verifier" shell settings put global verifier_verify_adb_installs 0 || true

test -s "$APP_APK"
test -s "$TEST_APK"
adb_retry "Install Cortex APK" install -r -g "$APP_APK"
adb_retry "Install Cortex instrumentation APK" install -r -g "$TEST_APK"

run_instrumentation() {
  set +e
  timeout 10m "${ADB[@]}" shell am instrument -w -r \
    com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner \
    >"$INSTRUMENTATION" 2>&1
  local rc=$?
  set -e
  return "$rc"
}

TEST_RC=0
run_instrumentation || TEST_RC=$?

if (( TEST_RC != 0 )); then
  if grep -Eqi 'device offline|no devices|device.*not found|unable to connect to adb daemon|cannot connect to daemon|closed|transport error|protocol fault' "$INSTRUMENTATION"; then
    echo "Instrumentation lost adb transport; recovering and retrying once."
    recover_adb
    TEST_RC=0
    run_instrumentation || TEST_RC=$?
  fi
fi

cat "$INSTRUMENTATION"
if (( TEST_RC != 0 )); then
  echo "Instrumentation command failed with exit code $TEST_RC."
  exit "$TEST_RC"
fi
grep -q '^OK (' "$INSTRUMENTATION"

require_android
adb_cmd logcat -d >"$LOGCAT" || true
adb_retry "Force-stop Cortex" shell am force-stop com.night.cortex || true
adb_retry "Launch Cortex" shell am start -W -n com.night.cortex/.MainActivity
sleep 3
require_android
adb_cmd exec-out screencap -p >"$SCREENSHOT"
test -s "$SCREENSHOT"

echo "Cortex API 36 instrumentation and screenshot capture passed."
