#!/usr/bin/env bash
set -euo pipefail

HOMIRA_SMOKE_API="${1:?API level required}"
ARTIFACT_DIR="homira-android/app/build/ui-smoke"
mkdir -p "$ARTIFACT_DIR"

capture_final_logcat() {
  adb logcat -d > "$ARTIFACT_DIR/final-logcat.txt" 2>/dev/null || true
}
trap capture_final_logcat EXIT

adb wait-for-device
adb install -r homira-android/app/build/outputs/apk/debug/app-debug.apk
mkdir -p homira-android/app/build/ui-smoke
adb logcat -c || true
adb shell am force-stop com.night.homira
adb shell am start -W -n com.night.homira/.HomiraSmokeActivity

sleep 1
adb shell pidof com.night.homira | grep -q .

cp .github/scripts/homira_ui_node.py /tmp/homira-ui-node.py

dump_ui() {
  rm -f /tmp/homira-window.xml
  adb shell rm -f /sdcard/homira-window.xml >/dev/null 2>&1 || true

  for attempt in 1 2 3 4 5 6; do
    if adb shell uiautomator dump --compressed /sdcard/homira-window.xml >/dev/null 2>&1 &&
       adb shell test -s /sdcard/homira-window.xml &&
       adb pull /sdcard/homira-window.xml /tmp/homira-window.xml >/dev/null 2>&1 &&
       test -s /tmp/homira-window.xml; then
      return 0
    fi

    echo "UI dump attempt $attempt failed; waiting for accessibility tree..."
    sleep 0.45
  done

  echo "Could not capture Homira accessibility tree"
  adb shell dumpsys window windows | tail -120 || true
  return 1
}

assert_ui() {
  dump_ui
  python3 /tmp/homira-ui-node.py assert "$1"
}

assert_ui_after_scroll() {
  label="$1"
  for attempt in 1 2 3 4; do
    if dump_ui &&
       python3 /tmp/homira-ui-node.py assert "$label" >/dev/null 2>&1; then
      echo "Found UI node: '$label'"
      return 0
    fi

    adb shell input swipe 540 1500 540 520 350
    sleep 0.45
  done

  dump_ui
  python3 /tmp/homira-ui-node.py assert "$label"
}

tap_ui() {
  dump_ui
  coords="$(python3 /tmp/homira-ui-node.py tap "$1")"
  set -- $coords
  adb shell input tap "$1" "$2"
  sleep 0.8
}

assert_clean_app_log() {
  file="$1"
  if grep -Eiq                 'FATAL EXCEPTION|Process: com\.night\.homira|coroutine scope left the composition|ANR in com\.night\.homira'                 "$file"; then
    echo "Homira runtime failure found in $file"
    grep -Ein                   'FATAL EXCEPTION|Process: com\.night\.homira|coroutine scope left the composition|ANR in com\.night\.homira'                   "$file" || true
    exit 1
  fi
}

assert_ui "Keypad"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/keypad.png

tap_ui "Recents"
assert_ui "Recents"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/recents.png

tap_ui "Filter calls"
assert_ui "Missed calls"
assert_ui "Rejected calls"
assert_ui "Outgoing calls"
assert_ui "Incoming calls"
assert_ui "Direct voicemail"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/recents-filter.png
adb shell input keyevent 4
sleep 0.5

tap_ui "Contacts"
assert_ui "Contacts"
assert_ui "Ada"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/contacts.png

tap_ui "Ada"
assert_ui "Voice"
assert_ui "Info"
assert_ui "Video"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/contact-actions.png

tap_ui "Info"
assert_ui "Favorite"
assert_ui "Share contact"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/contact-info-top.png
assert_ui_after_scroll "Delete contact"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/contact-info.png
adb shell input keyevent 4
sleep 0.5

tap_ui "Me"
assert_ui "Me"
assert_ui "Profile"
assert_ui "Calls"
assert_ui "Privacy"
assert_ui "Notifications"
assert_ui "Account"
assert_ui "About"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/me.png

tap_ui "Contacts"
tap_ui "Ada"
tap_ui "Voice"
assert_ui "Mute"
assert_ui "Speaker"
assert_ui "Video"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/voice-call.png
tap_ui "End call"
sleep 0.8

tap_ui "Keypad"
assert_ui "Keypad"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/keypad-return.png

adb logcat --pid="$(adb shell pidof com.night.homira)" -d > homira-android/app/build/ui-smoke/logcat.txt
assert_clean_app_log homira-android/app/build/ui-smoke/logcat.txt

adb logcat -c || true
adb shell am force-stop com.night.homira
adb shell am start -W -n com.night.homira/.HomiraWaitingCallSmokeActivity
sleep 1
tap_ui "Contacts"
tap_ui "Ada"
tap_ui "Voice"
assert_ui "Answer incoming call"
assert_ui "Decline incoming call"
assert_ui "Incoming voice call"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/waiting-call.png

tap_ui "Answer incoming call"
assert_ui "Tobi"
assert_ui "Mute"
assert_ui "Speaker"
adb exec-out screencap -p > homira-android/app/build/ui-smoke/waiting-call-answered.png
tap_ui "End call"
sleep 0.5

adb logcat --pid="$(adb shell pidof com.night.homira)" -d > homira-android/app/build/ui-smoke/waiting-call-logcat.txt
assert_clean_app_log homira-android/app/build/ui-smoke/waiting-call-logcat.txt

adb logcat -c || true
adb shell am force-stop com.night.homira
adb shell am start -W -n com.night.homira/.HomiraLiveSmokeActivity
sleep 8
adb logcat -d > homira-android/app/build/ui-smoke/live-logcat.txt
assert_clean_app_log homira-android/app/build/ui-smoke/live-logcat.txt
adb shell pidof com.night.homira | grep -q .
adb exec-out screencap -p > homira-android/app/build/ui-smoke/live-main.png

adb logcat -c || true
adb shell am force-stop com.night.homira
adb shell am start -W -n com.night.homira/.HomiraCachedStartupSmokeActivity
sleep 5
adb logcat -d > homira-android/app/build/ui-smoke/cached-startup-logcat.txt
assert_clean_app_log homira-android/app/build/ui-smoke/cached-startup-logcat.txt
adb shell pidof com.night.homira | grep -q .
adb exec-out screencap -p > homira-android/app/build/ui-smoke/cached-startup.png

if [ "$HOMIRA_SMOKE_API" = "36" ]; then
  adb uninstall com.night.homira
  adb install homira-android/app/build/outputs/apk/release/homira-release-smoke.apk
  adb logcat -c || true
  adb shell am start -W -n com.night.homira/.MainActivity
  sleep 5
  adb logcat -d > homira-android/app/build/ui-smoke/release-startup-logcat.txt
  assert_clean_app_log homira-android/app/build/ui-smoke/release-startup-logcat.txt
  adb shell pidof com.night.homira | grep -q .
  adb exec-out screencap -p > homira-android/app/build/ui-smoke/release-startup.png
fi

