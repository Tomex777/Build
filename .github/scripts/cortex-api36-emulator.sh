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

# These settings reduce emulator install/test interference, but they are not
# themselves validation requirements. The APK install and instrumentation below are.
adb_retry "Disable package verifier" shell settings put global package_verifier_enable 0 || true
adb_retry "Disable adb install verifier" shell settings put global verifier_verify_adb_installs 0 || true

test -s "$APP_APK"
test -s "$TEST_APK"
adb_retry "Install Cortex APK" install -r -g "$APP_APK"
adb_retry "Install Cortex instrumentation APK" install -r -g "$TEST_APK"

run_instrumentation() {
  wait_for_android
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
  if grep -Eqi 'device offline|no devices|device.*not found|unable to connect to adb daemon|cannot connect to daemon|closed|transport error|protocol fault|can.t find service: (package|activity|settings)' "$INSTRUMENTATION"; then
    echo "Instrumentation hit an Android/adb transport failure; waiting for a stable framework and retrying once."
    recover_transport
    wait_for_android
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

wait_for_android
adb_cmd logcat -d >"$LOGCAT" || true
adb_retry "Force-stop Cortex" shell am force-stop com.night.cortex || true
adb_retry "Launch Cortex" shell am start -W -n com.night.cortex/.MainActivity
sleep 3
wait_for_android
adb_cmd exec-out screencap -p >"$SCREENSHOT"
test -s "$SCREENSHOT"

echo "Cortex API 36 instrumentation and screenshot capture passed."
