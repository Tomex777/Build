#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/slumber-088/app/build/outputs/apk/debug/app-debug.apk}"
OUT=/tmp/slumber-api26-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"

# API 26 emulators can be briefly unresponsive after install or during a cold
# Compose launch. Keep the harness bounded, but do not turn a transient 30 s
# ADB stall into SIGKILL 137 and misdiagnose it as an application failure.
adb_with_timeout() {
  local seconds="$1"; shift
  timeout --foreground --signal=TERM --kill-after=10s "${seconds}s" adb "$@"
}
adb_quick() {
  adb_with_timeout 30 "$@"
}
adb_slow() {
  adb_with_timeout 90 "$@"
}
adb_retry() {
  local attempts="$1"; local seconds="$2"; shift 2
  local n
  for n in $(seq 1 "$attempts"); do
    if adb_with_timeout "$seconds" "$@"; then
      return 0
    fi
    echo "API26: adb attempt $n/$attempts failed: adb $*" >&2
    adb_with_timeout 45 wait-for-device >/dev/null 2>&1 || true
    sleep "$n"
  done
  return 1
}
launch_app() {
  # Do not use am start -W here. On API 26 with software rendering, -W can
  # block on first-draw bookkeeping long after ActivityManager has accepted
  # the launch. UIAutomator below is the real startup/ready assertion.
  adb_retry 3 60 shell am force-stop "$PKG" >/dev/null
  adb_retry 3 60 shell am start -n "$ACTIVITY" >/dev/null
}

echo "API26: verify device"
adb_with_timeout 180 wait-for-device
for _ in $(seq 1 90); do
  if [ "$(adb_quick shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
    break
  fi
  sleep 2
done
test "$(adb_quick shell getprop sys.boot_completed | tr -d '\r')" = "1"
adb_quick shell settings get global device_provisioned >/dev/null || true
adb_quick logcat -c || true

dump_ui() {
  rm -f "$OUT/ui.xml"
  adb_with_timeout 30 shell uiautomator dump /sdcard/slumber-api26.xml >/dev/null 2>&1 || true
  adb_with_timeout 30 pull /sdcard/slumber-api26.xml "$OUT/ui.xml" >/dev/null 2>&1 || true

  # Android 8 shows a one-time immersive-mode education overlay the first time
  # Piano enters fullscreen landscape. It belongs to package android and hides
  # Slumber's semantics. Dismiss it from the same dump, then refresh once.
  local xy
  xy="$(python3 - "$OUT/ui.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(0)
for node in root.iter("node"):
    a=node.attrib
    text=a.get("text","").strip().lower()
    rid=a.get("resource-id","")
    package=a.get("package","")
    if package=="android" and (rid=="android:id/ok" or text in {"got it","ok"}):
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2,(y1+y2)//2)
            raise SystemExit(0)
PY
  )" || true
  if [ -n "${xy:-}" ]; then
    echo "API26: dismiss Android immersive education overlay" >&2
    adb_quick shell input tap $xy || true
    sleep 1
    rm -f "$OUT/ui.xml"
    adb_with_timeout 30 shell uiautomator dump /sdcard/slumber-api26.xml >/dev/null 2>&1 || true
    adb_with_timeout 30 pull /sdcard/slumber-api26.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
  fi
}

ui_has() {
  local needle="$1"
  dump_ui
  python3 - "$OUT/ui.xml" "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: raise SystemExit(1)
needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if a.get("text","").strip()==needle or a.get("content-desc","").strip()==needle:
        raise SystemExit(0)
raise SystemExit(1)
PY
}
wait_for() {
  local needle="$1"; local seconds="${2:-30}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$needle"; then return 0; fi
    sleep 1
  done
  adb_with_timeout 45 exec-out screencap -p > "$OUT/failure.png" || true
  adb_with_timeout 45 logcat -d -t 2500 > "$OUT/failure-logcat.txt" || true
  adb_with_timeout 30 shell dumpsys activity activities > "$OUT/failure-activities.txt" || true
  echo "API 26 timed out waiting for: $needle" >&2
  return 1
}
tap_ui() {
  local needle="$1"
  dump_ui
  local xy
  xy="$(python3 - "$OUT/ui.xml" "$needle" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]
for node in root.iter("node"):
    a=node.attrib
    if a.get("text","").strip()==needle or a.get("content-desc","").strip()==needle:
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit(1)
PY
)"
  adb_quick shell input tap $xy
}

tap_until_visible() {
  local source="$1"; local target="$2"; local seconds="${3:-35}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$target"; then return 0; fi
    if ui_has "$source"; then tap_ui "$source" || true; fi
    sleep 1
  done
  echo "API 26 could not reach '$target' from '$source'" >&2
  adb_with_timeout 45 exec-out screencap -p > "$OUT/transition-failure.png" || true
  return 1
}

echo "API26: install APK"
adb_with_timeout 240 install --no-streaming -r "$APK"
echo "API26: clear and launch"
adb_retry 3 90 shell pm clear "$PKG" >/dev/null
launch_app
wait_for "Practice" 60
wait_for "Falling notes" 20

tap_ui "Songs"
wait_for "Learn a song" 35
wait_for "C major warm-up" 15
tap_ui "Practice"
wait_for "Practice" 25

tap_until_visible "Start practice" "88 keys" 45
wait_for "88 keys" 15
adb_with_timeout 45 exec-out screencap -p > "$OUT/piano-api26.png"
test -s "$OUT/piano-api26.png"
adb_quick shell input keyevent KEYCODE_BACK
wait_for "Practice" 35

tap_ui "Settings"
wait_for "Make Slumber yours" 35
wait_for "Sounds" 15
adb_with_timeout 45 exec-out screencap -p > "$OUT/settings-api26.png"
test -s "$OUT/settings-api26.png"

launch_app
wait_for "Practice" 45
adb_quick shell pidof "$PKG" >/dev/null

adb_with_timeout 45 logcat -d -t 5000 > "$OUT/api26-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/api26-logcat.txt"; then
  echo "Slumber crashed on API 26" >&2
  exit 1
fi

cat > "$OUT/GREEN.txt" <<'TXT'
API 26 INSTALL = GREEN
API 26 COLD START = GREEN
API 26 PRACTICE = GREEN
API 26 PIANO = GREEN
API 26 SONGS = GREEN
API 26 SETTINGS = GREEN
API 26 PROCESS RELAUNCH = GREEN
TXT
cat "$OUT/GREEN.txt"
