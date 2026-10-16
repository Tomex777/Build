#!/usr/bin/env bash
set -euo pipefail

output=${1:?Usage: capture_settings_hierarchy.sh /path/to/hierarchy.xml}
mkdir -p "$(dirname "$output")"
remote="/sdcard/yomi-reader-settings.xml"
rm -f "$output"

# UIAutomator occasionally gets a null root on an otherwise responsive API26
# emulator. Only accept a *fresh* accessibility dump with both real settings
# tabs; retry transient instrumentation failures without faking a pass.
for attempt in 1 2 3 4 5; do
    adb shell rm -f "$remote" || true
    if timeout 35s adb shell uiautomator dump --compressed "$remote" &&
       adb pull "$remote" "$output" &&
       grep -Fq 'Reading mode' "$output" &&
       grep -Fq 'Color filter' "$output"; then
        echo "Verified Mihon reader settings accessibility on attempt $attempt"
        exit 0
    fi
    rm -f "$output"
    echo "Settings accessibility tree not ready (attempt $attempt/5)" >&2
    sleep 6
done

adb shell dumpsys activity activities > "${output%.xml}-failure-activity.txt" || true
adb logcat -d -v threadtime > "${output%.xml}-failure-logcat.txt" || true
echo "Could not verify visible Mihon settings on the device; not accepting logs alone." >&2
exit 1
