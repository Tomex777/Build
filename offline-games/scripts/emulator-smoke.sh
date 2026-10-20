#!/usr/bin/env bash
set -euo pipefail

# An external script ensures the working directory survives every command.
# GitHub's emulator-runner executes separate lines in separate shell invocations.
adb shell svc wifi disable || true
adb shell svc data disable || true
gradle --no-daemon -p offline-games :app:connectedDebugAndroidTest
mkdir -p offline-games/smoke-screenshots
adb pull /sdcard/Android/data/com.night.offlinegames/files/. offline-games/smoke-screenshots/ || true
