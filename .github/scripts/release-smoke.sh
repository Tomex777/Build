#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${1:?API level is required}"
APK="${2:?APK path is required}"
PACKAGE="com.night.mirrorchess"
ACTIVITY="$PACKAGE/.MainActivity"

dismiss_system_anr_overlay() {
  local bounds x y
  bounds="$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
try:
    root = ET.parse("release-window.xml").getroot()
except Exception:
    raise SystemExit(0)
has_anr = any(
    "isn't responding" in node.attrib.get("text", "")
    for node in root.iter()
)
if not has_anr:
    raise SystemExit(0)
for node in root.iter():
    if node.attrib.get("text", "") == "Wait":
        nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
        if len(nums) == 4:
            print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
            raise SystemExit(0)
PY
)"
  if [[ -n "$bounds" ]]; then
    read -r x y <<< "$bounds"
    echo "Dismissing emulator system ANR overlay via Wait at $x,$y"
    adb shell input tap "$x" "$y" || true
    sleep 1
    return 0
  fi
  return 1
}

wait_for_ui() {
  local attempt
  for attempt in $(seq 1 20); do
    if adb shell uiautomator dump /sdcard/release-window.xml >/dev/null 2>&1; then
      adb shell cat /sdcard/release-window.xml > release-window.xml
      if [[ -s release-window.xml ]]; then
        # API 36 hosted images occasionally surface a Launcher3/Quickstep ANR
        # above the foreground app. It is unrelated to MirrorChess. Dismiss
        # only the system "Wait" overlay; app crashes/ANRs remain hard failures.
        if dismiss_system_anr_overlay; then
          continue
        fi
        return 0
      fi
    fi
    sleep 1
  done
  echo "UI hierarchy did not become available on API $API_LEVEL" >&2
  return 1
}

find_bounds() {
  local query="$1"
  local mode="${2:-text}"
  python3 - "$query" "$mode" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

query, mode = sys.argv[1:]
root = ET.parse("release-window.xml").getroot()
for node in root.iter():
    if mode in ("desc", "desc-prefix"):
        value = node.attrib.get("content-desc", "")
    else:
        value = node.attrib.get("text", "")
    matches = value.startswith(query) if mode == "desc-prefix" else value == query
    if matches and node.attrib.get("enabled", "true") == "true":
        nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
        if len(nums) == 4:
            print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_node() {
  local query="$1"
  local mode="${2:-text}"
  local attempt bounds x y
  for attempt in $(seq 1 20); do
    wait_for_ui
    if bounds="$(find_bounds "$query" "$mode")"; then
      read -r x y <<< "$bounds"
      echo "tap [$mode] $query at $x,$y"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "Could not find release UI node: $query ($mode)" >&2
  cat release-window.xml >&2 || true
  return 1
}

echo "Installing MirrorChess release candidate on API $API_LEVEL"
adb install "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c || true
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee release-launch.txt
grep -q 'Status: ok' release-launch.txt
sleep 5
adb shell pidof "$PACKAGE" | tee release-pid.txt
test -s release-pid.txt
adb exec-out screencap -p > "mirrorchess-release-home-api-$API_LEVEL.png"

tap_node "Start game"
tap_node "e2, white pawn" desc
tap_node "e4, empty" desc-prefix
sleep 5
adb shell pidof "$PACKAGE" >/dev/null
adb exec-out screencap -p > "mirrorchess-release-game-api-$API_LEVEL.png"

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" > release-relaunch.txt
grep -q 'Status: ok' release-relaunch.txt
sleep 3
wait_for_ui
grep -q 'Resume game' release-window.xml

# Re-enter the persisted game after process death and prove the actual position
# survived, not merely the presence of a resume affordance on Home.
tap_node "Resume game"
wait_for_ui
if ! find_bounds "e4, white pawn" desc >/dev/null; then
  echo "Persisted game did not restore the white pawn on e4 after process death" >&2
  cat release-window.xml >&2 || true
  exit 1
fi
adb shell pidof "$PACKAGE" >/dev/null
adb exec-out screencap -p > "mirrorchess-release-resumed-api-$API_LEVEL.png"

adb logcat -d > release-logcat.txt
if grep -A 50 'FATAL EXCEPTION' release-logcat.txt | grep -q "$PACKAGE"; then
  echo "MirrorChess release crashed on API $API_LEVEL" >&2
  exit 1
fi
if grep -Eq 'ANR in com\.night\.mirrorchess|Application Not Responding: com\.night\.mirrorchess|am_anr.*com\.night\.mirrorchess' release-logcat.txt; then
  echo "MirrorChess release ANR on API $API_LEVEL" >&2
  exit 1
fi

echo "RELEASE_SMOKE_OK api=$API_LEVEL"
