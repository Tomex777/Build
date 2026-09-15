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

SCREEN_HEIGHT="$(adb shell wm size | tr -d '\r' | sed -n 's/.*x\([0-9][0-9]*\)$/\1/p' | tail -1)"
case "$SCREEN_HEIGHT" in
  ''|*[!0-9]*) echo "Unable to read emulator screen height"; exit 1 ;;
esac

ime_top_from_dump() {
  python3 - "$1" "$SCREEN_HEIGHT" <<'PY'
import re
import sys
path, screen_height = sys.argv[1], int(sys.argv[2])
s = open(path, encoding='utf-8').read()
m = re.search(r'mNavigationBarFrame=.*?\s0,(\d+)-\d+,(\d+)\}', s)
if not m:
    raise SystemExit('NavigationBarFrame not found in ' + path)
nav_top, nav_bottom = map(int, m.groups())
print(screen_height - nav_bottom)
PY
}

nav_height_from_dump() {
  python3 - "$1" <<'PY'
import re
import sys
s = open(sys.argv[1], encoding='utf-8').read()
m = re.search(r'mNavigationBarFrame=.*?\s0,(\d+)-\d+,(\d+)\}', s)
if not m:
    raise SystemExit('NavigationBarFrame not found')
a, b = map(int, m.groups())
print(b - a)
PY
}

assert_xml_text() {
  local file="$1"
  local expected="$2"
  python3 - "$file" "$expected" <<'PY'
import sys
import xml.etree.ElementTree as ET
path, expected = sys.argv[1], sys.argv[2]
texts = [node.attrib.get('text', '') for node in ET.parse(path).iter()]
if expected not in texts:
    print(f'Expected UI text not found in {path}: {expected!r}')
    for text in texts:
        if text:
            print(repr(text))
    raise SystemExit(1)
PY
}

IME_TOP="$(ime_top_from_dump input-method.txt)"
NAV_HEIGHT="$(nav_height_from_dump input-method.txt)"
echo "screen_height_px=$SCREEN_HEIGHT ime_top_px=$IME_TOP ime_navigation_bar_height_px=$NAV_HEIGHT"

# Local coordinates are measured inside the fixed Pixel 6 production IME. They
# remain stable when the IME window grows upward because we anchor them to the
# window's measured top instead of shifting every control by the nav-bar height.
Q_X=54
W_X=161
Q_ROW_LOCAL_Y=235
SHIFT_X=68
BACKSPACE_X=1006
BACKSPACE_LOCAL_Y=492
BOTTOM_LEFT_X=60
SECOND_BOTTOM_X=190
SPACE_X=545
SPACE_LOCAL_Y=620
TOOLBAR_EMOJI_X=174
TOOLBAR_LOCAL_Y=61
EMOJI_FIRST_X=75
EMOJI_FIRST_LOCAL_Y=196

Q_Y=$(( IME_TOP + Q_ROW_LOCAL_Y ))
SHIFT_Y=$(( IME_TOP + BACKSPACE_LOCAL_Y ))
BACKSPACE_Y=$(( IME_TOP + BACKSPACE_LOCAL_Y ))
BOTTOM_ROW_Y=$(( IME_TOP + SPACE_LOCAL_Y ))
SPACE_Y=$(( IME_TOP + SPACE_LOCAL_Y ))
TOOLBAR_Y=$(( IME_TOP + TOOLBAR_LOCAL_Y ))

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

# Tap the real Q key and prove text and selection reach the host editor.
adb shell input tap "$Q_X" "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-q.xml
adb pull /sdcard/after-q.xml after-q.xml
assert_xml_text after-q.xml "qCursor test: move the caret through this sentence"
assert_xml_text after-q.xml "Selection: 1-1"

# One continuous swipe crosses the production long-press gate. The gesture must
# move the actual host caret left without leaking a space character.
adb shell input swipe "$SPACE_X" "$SPACE_Y" 445 "$SPACE_Y" 1200
sleep 1
adb shell uiautomator dump /sdcard/after-trackpad.xml
adb pull /sdcard/after-trackpad.xml after-trackpad.xml
assert_xml_text after-trackpad.xml "qCursor test: move the caret through this sentence"
assert_xml_text after-trackpad.xml "Selection: 0-0"

# Commit W after the moved caret as a second end-to-end cursor proof.
adb shell input tap "$W_X" "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-cursor.xml
adb pull /sdcard/after-cursor.xml after-cursor.xml
assert_xml_text after-cursor.xml "wqCursor test: move the caret through this sentence"

# Single Backspace must delete the character before the caret.
adb shell input tap "$BACKSPACE_X" "$BACKSPACE_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-backspace.xml
adb pull /sdcard/after-backspace.xml after-backspace.xml
assert_xml_text after-backspace.xml "qCursor test: move the caret through this sentence"
adb exec-out screencap -p > ime-qa-after-input.png

# Exercise the visible Emoji toolbar. Opening the panel changes the IME height,
# so re-measure the window before touching panel content rather than guessing how
# far the toolbar moved on screen.
adb shell input tap "$TOOLBAR_EMOJI_X" "$TOOLBAR_Y"
sleep 1
adb shell dumpsys input_method > input-method-emoji.txt
adb exec-out screencap -p > emoji-panel.png
EXPANDED_IME_TOP="$(ime_top_from_dump input-method-emoji.txt)"
EMOJI_FIRST_Y=$(( EXPANDED_IME_TOP + EMOJI_FIRST_LOCAL_Y ))
EXPANDED_TOOLBAR_Y=$(( EXPANDED_IME_TOP + TOOLBAR_LOCAL_Y ))
echo "expanded_ime_top_px=$EXPANDED_IME_TOP emoji_first_y=$EMOJI_FIRST_Y"
adb shell input tap "$EMOJI_FIRST_X" "$EMOJI_FIRST_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-emoji.xml
adb pull /sdcard/after-emoji.xml after-emoji.xml
assert_xml_text after-emoji.xml "🙂qCursor test: move the caret through this sentence"

# Close the expanded emoji panel using the toolbar position in the expanded
# window, then re-measure the normal IME before exercising keyboard layers.
adb shell input tap "$TOOLBAR_EMOJI_X" "$EXPANDED_TOOLBAR_Y"
sleep 1
adb shell dumpsys input_method > input-method-normal.txt
NORMAL_IME_TOP="$(ime_top_from_dump input-method-normal.txt)"
Q_Y=$(( NORMAL_IME_TOP + Q_ROW_LOCAL_Y ))
SHIFT_Y=$(( NORMAL_IME_TOP + BACKSPACE_LOCAL_Y ))
BACKSPACE_Y=$(( NORMAL_IME_TOP + BACKSPACE_LOCAL_Y ))
BOTTOM_ROW_Y=$(( NORMAL_IME_TOP + SPACE_LOCAL_Y ))

# 123 is a real keyboard-mode key, not a substitute number row. Verify the
# alphabet -> common symbols -> more symbols -> alphabet path and actual output.
adb shell input tap "$BOTTOM_LEFT_X" "$BOTTOM_ROW_Y"
sleep 1
adb exec-out screencap -p > symbol-pane.png
adb shell input tap "$Q_X" "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-symbol-one.xml
adb pull /sdcard/after-symbol-one.xml after-symbol-one.xml
assert_xml_text after-symbol-one.xml "🙂1qCursor test: move the caret through this sentence"

adb shell input tap "$SECOND_BOTTOM_X" "$BOTTOM_ROW_Y"
sleep 1
adb exec-out screencap -p > more-symbols-pane.png
adb shell input tap "$Q_X" "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-more-symbol.xml
adb pull /sdcard/after-more-symbol.xml after-more-symbol.xml
assert_xml_text after-more-symbol.xml "🙂1~qCursor test: move the caret through this sentence"

# Return through the secondary-symbol 123 key to common symbols, then ABC back
# to letters. A shifted Q proves the restored alphabet layer and Shift behavior.
adb shell input tap "$SECOND_BOTTOM_X" "$BOTTOM_ROW_Y"
sleep 1
adb shell input tap "$BOTTOM_LEFT_X" "$BOTTOM_ROW_Y"
sleep 1
adb exec-out screencap -p > letters-restored.png
adb shell input tap "$SHIFT_X" "$SHIFT_Y"
adb shell input tap "$Q_X" "$Q_Y"
sleep 1
adb shell uiautomator dump /sdcard/after-shift.xml
adb pull /sdcard/after-shift.xml after-shift.xml
assert_xml_text after-shift.xml "🙂1~QqCursor test: move the caret through this sentence"

# Remove the shifted probe so the repeat test starts from a predictable caret.
adb shell input tap "$BACKSPACE_X" "$BACKSPACE_Y"
sleep 1

# Insert a safe run of q characters, then hold Backspace without movement. More
# than one character must disappear, proving stationary repeat rather than a tap.
for _ in 1 2 3 4 5 6 7 8; do adb shell input tap "$Q_X" "$Q_Y"; done
sleep 1
adb shell uiautomator dump /sdcard/before-repeat-backspace.xml
adb pull /sdcard/before-repeat-backspace.xml before-repeat-backspace.xml
adb shell input swipe "$BACKSPACE_X" "$BACKSPACE_Y" "$BACKSPACE_X" "$BACKSPACE_Y" 720
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
