#!/usr/bin/env bash
set -euo pipefail

APK="${1:?Universal release QA APK path required}"
API="${2:?API level required}"
OUT="${3:-/tmp/slumber-release-api${API}-proof}"
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"
test -s "$APK"

adb wait-for-device
adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm clear "$PKG" >/dev/null
adb logcat -c || true
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/launch.txt"
grep -q 'Status: ok' "$OUT/launch.txt"
sleep 3
adb shell pidof "$PKG" | tee "$OUT/pid-first.txt"
test -s "$OUT/pid-first.txt"

adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/relaunch.txt"
grep -q 'Status: ok' "$OUT/relaunch.txt"
sleep 2
adb shell pidof "$PKG" | tee "$OUT/pid-relaunch.txt"
test -s "$OUT/pid-relaunch.txt"

adb shell dumpsys package "$PKG" > "$OUT/package.txt"
adb logcat -d -t 3000 > "$OUT/logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/logcat.txt"; then
  echo "Slumber release crashed on API $API" >&2
  exit 1
fi
cat > "$OUT/GREEN.txt" <<TXT
UNIVERSAL RELEASE INSTALL API $API = GREEN
UNIVERSAL RELEASE LAUNCH API $API = GREEN
UNIVERSAL RELEASE RELAUNCH API $API = GREEN
TXT
cat "$OUT/GREEN.txt"
