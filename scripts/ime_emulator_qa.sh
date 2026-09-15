#!/usr/bin/env bash
set -euo pipefail

gradle --no-daemon :app:connectedDebugAndroidTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Hosted Android emulators still advertise a hardware QWERTY device. Keep the
# ordinary software keyboard visible so this job exercises the production IME.
adb shell settings --user 0 put secure show_ime_with_hard_keyboard 1
echo "show_ime_with_hard_keyboard=$(adb shell settings --user 0 get secure show_ime_with_hard_keyboard | tr -d '\r')"

adb shell ime enable com.night.keyboard/.ime.KeyboardInputMethodService
adb shell ime set com.night.keyboard/.ime.KeyboardInputMethodService
adb shell am start -W -n com.night.keyboard/.debug.ImeHarnessActivity
sleep 4

adb shell dumpsys input_method > input-method.txt
adb logcat -d > logcat.txt
adb exec-out screencap -p > ime-qa.png
adb shell uiautomator dump /sdcard/ime-window.xml
adb pull /sdcard/ime-window.xml ime-window.xml

# Android 16 draws the navigation affordance inside the IME window. The Pixel 6
# profile reports the exact navigation frame in InputMethodService diagnostics.
# Production reserves the same bottom region, so move fixed-profile probes up by
# that measured height rather than hard-coding one navigation mode.
NAV_HEIGHT="$(python3 - <<'PY'
import re
s = open('input-method.txt', encoding='utf-8').read()
m = re.search(r'mNavigationBarFrame=.*?\s0,(\d+)-1080,(\d+)\}', s)
print(int(m.group(2)) - int(m.group(1)) if m else 0)
PY
)"
case "$NAV_HEIGHT" in
  ''|*[!0-9]*) echo "Invalid IME navigation height: $NAV_HEIGHT"; exit 1 ;;
esac
echo "ime_navigation_bar_height_px=$NAV_HEIGHT"
key_y() { echo $(( $1 - NAV_HEIGHT )); }

# Framework-level proof that the real InputMethodService window is live.
grep -q "mCurImeId=com.night.keyboard/.ime.KeyboardInputMethodService" input-method.txt
grep -q "mDecorViewVisible=true" input-method.txt
grep -q "mInputStarted=true mInputViewStarted=true" input-method.txt
grep -q "mIsInputViewShown=true" input-method.txt
if grep -E "FATAL EXCEPTION|AndroidRuntime" logcat.txt | grep -q "com.night.keyboard"; then
  echo "Keyboard process crash detected in startup logcat"
  grep -n -A24 -B6 "FATAL EXCEPTION" logcat.txt || true
  exit 1
fi

Q_Y="$(key_y 1935)"
SPACE_Y="$(key_y 2320)"
BACKSPACE_Y="$(key_y 2192)"
TOOLBAR_Y="$(key_y 1607)"
EMOJI_FIRST_Y="$(key_y 1685)"
EXPANDED_TOOLBAR_Y="$(key_y 1555)"

# Tap the real Q key and prove text and selection reach the host editor.
adb shell input tap 54 "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-q.xml
adb pull /sdcard/after-q.xml after-q.xml
grep -q 'text="qCursor test: move the caret through this sentence"' after-q.xml
grep -q 'text="Selection: 1-1"' after-q.xml

# One continuous swipe crosses the production long-press gate. The gesture must
# move the actual host caret left without leaking a space character.
adb shell input swipe 545 "$SPACE_Y" 445 "$SPACE_Y" 1200
sleep 1
adb shell uiautomator dump /sdcard/after-trackpad.xml
adb pull /sdcard/after-trackpad.xml after-trackpad.xml
grep -q 'text="qCursor test: move the caret through this sentence"' after-trackpad.xml
grep -q 'text="Selection: 0-0"' after-trackpad.xml

# Commit W after the moved caret as a second end-to-end cursor proof.
adb shell input tap 161 "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-cursor.xml
adb pull /sdcard/after-cursor.xml after-cursor.xml
grep -q 'text="wqCursor test: move the caret through this sentence"' after-cursor.xml

# Single Backspace must delete the character before the caret.
adb shell input tap 1006 "$BACKSPACE_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-backspace.xml
adb pull /sdcard/after-backspace.xml after-backspace.xml
grep -q 'text="qCursor test: move the caret through this sentence"' after-backspace.xml
adb exec-out screencap -p > ime-qa-after-input.png

# Exercise the visible Emoji toolbar on the fixed Pixel 6 QA profile. The panel
# artwork itself is custom vector rendering, while the committed output remains
# the corresponding Unicode emoji for normal Android text fields.
adb shell input tap 134 "$TOOLBAR_Y"
sleep 1
adb exec-out screencap -p > emoji-panel.png
adb shell input tap 75 "$EMOJI_FIRST_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-emoji.xml
adb pull /sdcard/after-emoji.xml after-emoji.xml
grep -q 'text="🙂qCursor test: move the caret through this sentence"' after-emoji.xml

# Close the expanded emoji panel; its panel height shifts the toolbar upward.
adb shell input tap 134 "$EXPANDED_TOOLBAR_Y"
sleep 1

# Insert a safe run of q characters, then hold Backspace without movement. More
# than one character must disappear, proving stationary repeat rather than a tap.
for _ in 1 2 3 4 5 6 7 8; do adb shell input tap 54 "$Q_Y"; done
sleep 1
adb shell uiautomator dump /sdcard/before-repeat-backspace.xml
adb pull /sdcard/before-repeat-backspace.xml before-repeat-backspace.xml
adb shell input swipe 1006 "$BACKSPACE_Y" 1006 "$BACKSPACE_Y" 720
sleep 1
adb shell uiautomator dump /sdcard/after-repeat-backspace.xml
adb pull /sdcard/after-repeat-backspace.xml after-repeat-backspace.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
phrase = 'Cursor test: move the caret through this sentence'
before = next(n.attrib['text'] for n in ET.parse('before-repeat-backspace.xml').iter() if phrase in n.attrib.get('text', ''))
after = next(n.attrib['text'] for n in ET.parse('after-repeat-backspace.xml').iter() if phrase in n.attrib.get('text', ''))
removed = len(before) - len(after)
assert removed >= 2, f'hold repeat removed only {removed} character(s): {before!r} -> {after!r}'
assert phrase in after
PY
adb exec-out screencap -p > after-repeat-backspace.png

# Setup is genuinely complete now. Normal Home must not retain onboarding or a
# permanent setup-success card after this IME is both enabled and selected.
adb shell am start -W -n com.night.keyboard/.MainActivity
sleep 2
adb shell uiautomator dump /sdcard/home-after-setup.xml
adb pull /sdcard/home-after-setup.xml home-after-setup.xml
if grep -q 'text="Finish setup"' home-after-setup.xml; then echo "Finish setup card remained after successful setup"; exit 1; fi
if grep -q 'text="Setup complete"' home-after-setup.xml; then echo "Permanent setup success card remained on Home"; exit 1; fi
if grep -q 'text="Keyboard is active"' home-after-setup.xml; then echo "Permanent keyboard-active card remained on Home"; exit 1; fi
adb exec-out screencap -p > home-after-setup.png

# Final crash scan after all interactions, not only startup.
adb logcat -d > logcat-final.txt
if grep -E "FATAL EXCEPTION|AndroidRuntime" logcat-final.txt | grep -q "com.night.keyboard"; then
  echo "Keyboard process crash detected after interaction suite"
  grep -n -A24 -B6 "FATAL EXCEPTION" logcat-final.txt || true
  exit 1
fi
