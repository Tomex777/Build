#!/usr/bin/env bash
set -euo pipefail

adb wait-for-device
adb shell getprop sys.boot_completed
adb install -r -g cortex-android/app/build/outputs/apk/debug/app-debug.apk
adb install -r -g cortex-android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
# An unaccelerated GitHub emulator can kill first-run app startup while
# dex compilation is blocking the main thread. The main acceptance harness
# already uses pre-compilation for this reason.
for package in com.night.cortex com.night.cortex.test; do
  compiled=0
  for attempt in 1 2 3; do
    if adb shell cmd package compile -m speed -f "$package"; then
      compiled=1
      break
    fi
    sleep 8
    adb wait-for-device
  done
  if [[ $compiled -ne 1 ]]; then
    echo "::error::Android package service failed to precompile $package"
    exit 1
  fi
done
# Let the activity/package services settle after the test APK install.
sleep 15
adb logcat -c || true

# Preserve instrumentation output and Android crash evidence even if the
# emulator kills the app process before the test runner can report a failure.
set +e
adb shell am instrument -w -r \
  -e class com.night.cortex.CortexPairingScreenTest \
  com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner \
  > cortex-account-ui-results.txt 2>&1
instrument_status=$?
set -e
cat cortex-account-ui-results.txt

# Retrieve any screenshot emitted by the dashboard test even when another
# independent instrumentation test fails. This helps debug actual UI rendering.
# The screenshot comes from a Compose-rendered view inside the emulator.
{
  # The screenshot comes from a Compose-rendered view inside the emulator,
  # never from synthetic desktop HTML or an invented screen.
  if adb exec-out run-as com.night.cortex cat cache/cortex-accounts-dashboard-emulator.png \
      > cortex-accounts-dashboard-emulator.png 2>/dev/null &&
      [[ -s cortex-accounts-dashboard-emulator.png ]]; then
    echo "Dashboard screenshot retrieved from Android app cache."
  else
    rm -f cortex-accounts-dashboard-emulator.png
    echo "::warning::Android dashboard screenshot not available."
  fi
}

if [[ $instrument_status -ne 0 ]] || ! grep -Eq 'OK \([0-9]+ tests?\)' cortex-account-ui-results.txt; then
  echo "::error::Account UI instrumentation failed (exit $instrument_status). Capturing Android crash logs."
  adb logcat -d -v threadtime '*:E' > cortex-account-ui-logcat.txt 2>&1 || true
  adb shell dumpsys activity processes > cortex-account-ui-processes.txt 2>&1 || true
  # Logcat contains only synthetic emulator data, not live WhatsApp sessions.
  grep -E -A 38 -B 5 'FATAL EXCEPTION|AndroidRuntime|Process crashed|SIGSEGV|OutOfMemoryError|Process .* has died|TestRunner|Instrumentation' \
    cortex-account-ui-logcat.txt | tail -n 300 || true
  exit 1
fi
