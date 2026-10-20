#!/usr/bin/env bash
set -euo pipefail

# Android emulator runs with Wi-Fi and mobile data disabled.
adb shell svc wifi disable || true
adb shell svc data disable || true

# Gradle uninstalls/reinstalls test APKs during connectedDebugAndroidTest; after
# Gradle exits, Android can delete app-scoped external screenshots. Harvest real
# captures WHILE tests run, rather than attempting the (previously empty) pull
# only after their app process has been removed.
mkdir -p offline-games/smoke-screenshots
(
  while true; do
    adb pull /sdcard/Android/data/com.night.offlinegames/files/. \
      offline-games/smoke-screenshots/ >/dev/null 2>&1 || true
    sleep 1
  done
) &
collector=$!
cleanup() {
  kill "$collector" 2>/dev/null || true
  wait "$collector" 2>/dev/null || true
}
trap cleanup EXIT

set +e
gradle --no-daemon -p offline-games :app:connectedDebugAndroidTest
status=$?
set -e

adb pull /sdcard/Android/data/com.night.offlinegames/files/. \
  offline-games/smoke-screenshots/ >/dev/null 2>&1 || true
cleanup
trap - EXIT
echo "Real screenshot evidence collected:"
find offline-games/smoke-screenshots -type f -name '*.png' -printf '%f\n' | sort
count=$(find offline-games/smoke-screenshots -type f -name '*.png' | wc -l)
if [ "$count" -lt 4 ]; then
  echo "Emulator ran, but fewer than 4 real screenshots were retained: $count" >&2
  if [ "$status" -eq 0 ]; then status=3; fi
fi
exit "$status"
