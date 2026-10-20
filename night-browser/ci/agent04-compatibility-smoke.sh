#!/usr/bin/env bash
# Actual Android emulator interaction test for Agent 4. No synthetic UI mocks.
set -euo pipefail

# Existing script installs the architecture-specific APK, launches the browser,
# taps native buttons and navigates to https://example.com, saving evidence.
source night-browser/ci/capture-screenshots.sh

capture_second_site() {
  local site="$1"
  local filename="$2"
  tap_ui "Home"
  tap_ui "$site"
  sleep 14
  capture "$filename"
}

# Visit two additional HTTPS origins via actual Start Page touch interaction.
capture_second_site "Wikipedia" "07-wikipedia-geckoview.png"
capture_second_site "GitHub" "08-github-geckoview.png"

# Fail on process death, not on harmless Gecko warnings.
adb shell dumpsys activity activities > night-browser/screenshots/agent04-activity.txt
if ! grep -q 'com.night.browser' night-browser/screenshots/agent04-activity.txt; then
  echo "NIGHT Browser process is not foreground after real site navigation" >&2
  exit 1
fi
adb logcat -d -t 15000 > night-browser/screenshots/agent04-logcat-full.txt
if grep -A 15 'FATAL EXCEPTION' night-browser/screenshots/agent04-logcat-full.txt |
    grep -q 'com.night.browser'; then
  echo "NIGHT Browser crashed during emulator interaction" >&2
  exit 1
fi
for image in 06-webpage.png 07-wikipedia-geckoview.png 08-github-geckoview.png; do
  test -s "night-browser/screenshots/$image"
done
echo "Installed APK and interacted with three HTTPS pages. Screenshots need human review."
