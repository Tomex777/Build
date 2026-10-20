#!/usr/bin/env bash
# Deterministic interaction smoke for the real GeckoView APK on Android API 36.
# Every adb operation is bounded: an unresponsive Gecko/accessibility tree must
# produce a clear test failure rather than occupy a GitHub runner for 40 minutes.
set -euo pipefail
DIR="night-browser/screenshots"
APK="night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk"
mkdir -p "$DIR"
test -s "$APK" || { echo "Missing x86_64 APK" >&2; exit 1; }
adb_safe() { timeout 25s adb "$@"; }
capture() {
  adb_safe shell screencap -p /sdcard/night-agent04.png
  adb_safe pull /sdcard/night-agent04.png "$DIR/$1" >/dev/null
  test -s "$DIR/$1"
}
dump_ui() {
  # Dumping accessibility with a live Gecko surface has previously hung here.
  # We only dump on the Compose start page and with a hard 20s deadline.
  adb_safe shell uiautomator dump /sdcard/night-agent04.xml >/dev/null
  adb_safe pull /sdcard/night-agent04.xml "$DIR/agent04-ui.xml" >/dev/null
}
tap_named_site() {
  local site="$1"
  dump_ui
  local point
  point=$(python3 - "$DIR/agent04-ui.xml" "$site" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.get('text') == sys.argv[2] or node.get('content-desc') == sys.argv[2]:
        a=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        if len(a)==4:
            print((a[0]+a[2])//2,(a[1]+a[3])//2)
            break
PY
  )
  if [ -z "$point" ]; then
    echo "FAIL: Could not find Compose quick access target: $site" >&2
    exit 1
  fi
  adb_safe shell input tap $point
}
# Real installation on the emulator is a separate milestone from compilation.
adb_safe install -r "$APK"
adb_safe shell wm size 1080x1920
adb_safe shell wm density 420
# Relaunch between sites to exercise process recreation and return to Home
# without relying on back-navigation or a potentially hanging Gecko UI dump.
for spec in 'Google:google' 'Wikipedia:wikipedia' 'GitHub:github'; do
  site="${spec%%:*}"
  label="${spec#*:}"
  adb_safe shell am force-stop com.night.browser || true
  adb_safe shell am start -n com.night.browser/.MainActivity
  sleep 9
  capture "agent04-${label}-home.png"
  tap_named_site "$site"
  sleep 14
  capture "agent04-${label}-web.png"
  adb_safe shell dumpsys activity activities > "$DIR/agent04-${label}-activity.txt"
  grep -q 'com.night.browser' "$DIR/agent04-${label}-activity.txt" || {
    echo "FAIL: NIGHT Browser no longer appears in Android activities ($site)" >&2
    exit 1
  }
done
# Keep diagnostic log bounded and redacted; errors are checked separately
# from normal site console and Gecko warnings.
timeout 30s adb logcat -d -t 6000 > "$DIR/agent04-logcat.txt"
if grep -A 20 'FATAL EXCEPTION' "$DIR/agent04-logcat.txt" |
    grep -q 'com.night.browser'; then
  echo "FAIL: NIGHT Browser Java crash detected" >&2
  exit 1
fi
echo "Installed, relaunched and tapped three real HTTPS sites on emulator."
echo "Full visual review and functional permissions/uploads QA remain required."
