#!/usr/bin/env bash
# Run the exact Agent 1 real-device classes in one instrumentation invocation.
set -euo pipefail
REPORT_ROOT="annie-android/build/agent1-omni-evidence"
mkdir -p "$REPORT_ROOT"

# Fully-qualified names matter: BookmarkStorageTest lives in the imported
# Omni engine package rather than com.tomex777.annie.
CLASSES=(
  com.tomex777.annie.AnnieGeckoTabRestoreTest
  com.tomex777.annie.AnnieOmniExtensionTest
  com.tomex777.annie.AnnieOmniPersistenceSecurityTest
  com.tomex777.annie.AnnieOmniUserAgentTest
  com.tomex777.annie.AnnieOmniSmartTabsTest
  com.tomex777.annie.AnnieOmniFindInPageTest
  com.tomex777.annie.AnnieOmniSitePermissionTest
  com.tomex777.annie.browser.engine.bookmarks.storage.AnnieOmniBookmarkStorageTest
  com.tomex777.annie.AnnieOmniBookmarkImportBridgeTest
  com.tomex777.annie.AnnieOmniIncomingIntentTest
  com.tomex777.annie.AnnieOmniSessionRestoreTest
  com.tomex777.annie.AnnieGeckoBrowserEndToEndTest
  com.tomex777.annie.AnnieGeckoBrowserUiTest
)

selected="$(IFS=,; echo "${CLASSES[*]}")"
status=0
timeout --signal=TERM --kill-after=30s 1800s \
  gradle --no-daemon --stacktrace -p annie-android :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$selected" || status=$?

mkdir -p "$REPORT_ROOT/outputs"
cp -R annie-android/app/build/outputs/androidTest-results/. "$REPORT_ROOT/outputs/" || status=1
python3 annie-android/ci/verify-omni-agent1-report.py "$REPORT_ROOT/outputs" || status=1
adb logcat -d > "$REPORT_ROOT/logcat.txt" || true
adb pull /sdcard/Pictures/AnnieCI "$REPORT_ROOT/screenshots" || true
exit "$status"
