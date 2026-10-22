#!/usr/bin/env bash
# Agent 2: actual installed-app Home and navigation exercises.
# These are Android UI inputs and screenshots, never mocked Compose renders.
set -euo pipefail
OUT="night-browser/screenshots/agent02-home"
mkdir -p "$OUT"
APK="night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk"
test -s "$APK"

capture() {
  local label="$1"
  adb shell screencap -p /sdcard/night-agent02.png >/dev/null
  adb pull /sdcard/night-agent02.png "$OUT/$label.png" >/dev/null
}
dump_ui() {
  adb shell uiautomator dump /sdcard/night-agent02.xml >/dev/null 2>&1
  adb pull /sdcard/night-agent02.xml "$OUT/ui.xml" >/dev/null
}
# Arguments: action (has|not|click|count_editor) and target string.
ui() {
  local operation="$1"
  local target="${2:-}"
  dump_ui
  python3 - "$OUT/ui.xml" "$operation" "$target" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
operation, target = sys.argv[2:4]
nodes = list(root.iter('node'))
matched = [node for node in nodes if target in (node.get('text'), node.get('content-desc'))]
if operation in ('has', 'not'):
    success = bool(matched)
    assert success == (operation == 'has'), f"Expected UI label {target!r} to be {operation}; visible labels: {[(n.get('text'),n.get('content-desc')) for n in nodes if n.get('text') or n.get('content-desc')][:50]}"
elif operation == 'editors':
    count = sum(n.get('class') == 'android.widget.EditText' for n in nodes)
    assert count == int(target), f"Expected {target} EditTexts, found {count}"
elif operation == 'click':
    assert matched, f"Cannot find clickable UI target {target!r}"
    node = matched[0]
    bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
    assert len(bounds) == 4, f"No touch bounds for {target!r}"
    print((bounds[0]+bounds[2])//2, (bounds[1]+bounds[3])//2)
elif operation == 'editcoords':
    editors = [n for n in nodes if n.get('class') == 'android.widget.EditText']
    index = int(target)
    assert len(editors) > index, f"Only {len(editors)} inputs visible"
    bounds = list(map(int, re.findall(r'\d+', editors[index].get('bounds', ''))))
    assert len(bounds) == 4, "EditText has no bounds"
    print((bounds[0]+bounds[2])//2,(bounds[1]+bounds[3])//2)
else:
    raise RuntimeError(f"Unknown operation: {operation}")
PY
}
touch() {
  local xy
  xy=$(ui click "$1")
  adb shell input tap $xy
  sleep 2
}
tap_editor() {
  local xy
  xy=$(ui editcoords "$1")
  adb shell input tap $xy
  sleep 1
}
# The shortcut grid extends below the first viewport on a 320dp phone.
# Reach offscreen entries with actual user swipes instead of assuming
# UIAutomator includes invisible Compose children.
scroll_to_label() {
  local label="$1"
  for _ in 1 2 3 4; do
    if ui has "$label" >/dev/null 2>&1; then return 0; fi
    adb shell input swipe 540 1450 540 600 380
    sleep 1
  done
  ui has "$label"
}
# Header controls are in the same scrolling Home column, not a sticky bar.
scroll_to_top() {
  for _ in 1 2 3 4; do
    adb shell input swipe 540 580 540 1450 380
    sleep 1
  done
}
shutdown_diagnostics() {
  local status="$?"
  adb logcat -d -t 4000 | grep -E 'FATAL EXCEPTION|AndroidRuntime|com.night.browser' | tail -250 > "$OUT/logcat.txt" || true
  if [ "$status" -ne 0 ]; then
    capture "failure" || true
    adb shell dumpsys activity activities > "$OUT/activity.txt" || true
    echo "Agent 2 emulator interactions FAILED (exit $status)" >&2
  fi
}
trap shutdown_diagnostics EXIT

adb install -r "$APK"
adb shell wm size 1080x1920
adb shell wm density 540 # 320dp usable display width
adb shell am force-stop com.night.browser || true
adb shell am start -n com.night.browser/.MainActivity
sleep 12
ui has "Quick access"
ui has "Search or enter address"
ui editors "1"
# Shortcuts intentionally hide their Add and Remove controls until Edit is tapped.
# Validate the actual Home first, then enable editing through the user-facing control.
ui not "Add"
capture "01-home-320dp"
touch "Edit"
ui has "Done"
scroll_to_label "Add"

# Exercise the actual user-editable shortcuts: tap Add, fill both custom
# Compose fields, save, then verify they persist after process restart.
touch "Add"
ui has "Add shortcut"
ui editors "2"
capture "02-add-dialog"
tap_editor 0
adb shell input text "AgentQA"
# On narrow 320dp devices the keyboard obscures the next input/Save.
# Hide it, re-query actual bounds, then refocus the Website field.
adb shell input keyevent 4
sleep 2
ui has "Add shortcut"
tap_editor 1
adb shell input text "example.org"
adb shell input keyevent 4
sleep 2
ui has "Add shortcut"
capture "02b-fields-filled-keyboard-closed"
touch "Save"
sleep 2
scroll_to_label "AgentQA"
capture "03-added-site"

# Relaunch validates that speed dials are actually stored locally.
adb shell am force-stop com.night.browser
adb shell am start -n com.night.browser/.MainActivity
sleep 7
scroll_to_label "AgentQA"
capture "04-relaunched-persisted"

# Exercise shortcut editing, then deletion (no second independent delete
# action is exposed on the normal non-editing tile).
scroll_to_top
touch "Edit"
scroll_to_label "AgentQA"
touch "AgentQA"
ui has "Edit shortcut"
tap_editor 0
adb shell input keyevent 123 # MOVE_END
for _ in 1 2 3 4 5 6 7; do adb shell input keyevent 67; done
adb shell input text "AgentEdited"
adb shell input keyevent 4
sleep 2
ui has "Edit shortcut"
touch "Save"
scroll_to_label "AgentEdited"
capture "05-shortcut-edited"
touch "Remove AgentEdited"
ui not "AgentEdited"
scroll_to_top
touch "Done"
capture "06-shortcut-removed"

adb shell wm density 440 # ~393dp
sleep 3
ui has "Quick access"
ui editors "1"
capture "07-home-393dp"

# Confirm local on-device suggestion lookup appears without a network request
# and can be dismissed. The UI is still exercised with real keyboard inputs.
# Search on a real Gecko session through the only Home address field.
touch "Search or enter address"
adb shell input text "example.com"
# API26 Gboard sometimes treats adb KEYCODE_ENTER as an IME newline/dismiss,
# even when Compose exposes ImeAction.Go. Use the same VISIBLE submit
# control real users can tap. Assert the address was actually typed first.
ui has "example.com"
touch "Go to address or search"
sleep 12
ui not "Quick access"
capture "08-navigated-geckoview"

# Home -> tab -> home, including simple orientation flow.
touch "Home"
ui has "Quick access"
touch "Tabs"
ui has "Tabs"
capture "09-tab-overview"
touch "Home"
ui has "Quick access"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 5
ui has "Quick access"
capture "10-landscape-home"

adb shell settings put system user_rotation 0
adb shell wm density reset
adb shell wm size reset
echo "Agent 2 Android HOME smoke: PASSED API $(adb shell getprop ro.build.version.sdk), 320dp, 393dp, landscape, persistence and real navigation."
