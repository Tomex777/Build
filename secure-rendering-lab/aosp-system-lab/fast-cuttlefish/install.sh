#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/SecureRenderingSystemLab-platform.apk"
PERMS="$HERE/privapp-permissions-com.tomex.securerenderlab.system.xml"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required." >&2
  exit 2
fi

if [[ ! -f "$APK" ]]; then
  echo "Missing $APK" >&2
  exit 2
fi

if [[ ! -f "$PERMS" ]]; then
  echo "Missing $PERMS" >&2
  exit 2
fi

adb wait-for-device

BUILD_TYPE="$(adb shell getprop ro.build.type | tr -d '\r')"
DEBUGGABLE="$(adb shell getprop ro.debuggable | tr -d '\r')"

echo "Connected build type: $BUILD_TYPE"
echo "ro.debuggable: $DEBUGGABLE"

if [[ "$DEBUGGABLE" != "1" ]]; then
  echo "This fast path requires a userdebug/eng AOSP image." >&2
  exit 3
fi

echo
echo "[1/6] Enabling adb root..."
adb root
adb wait-for-device

echo "[2/6] Preparing writable system partitions..."
set +e
REMOUNT_OUTPUT="$(adb remount -R 2>&1)"
REMOUNT_STATUS=$?
set -e
printf '%s\n' "$REMOUNT_OUTPUT"

# remount -R can reboot. Give adb time to reconnect either way.
adb wait-for-device
adb root
adb wait-for-device

if [[ $REMOUNT_STATUS -ne 0 ]]; then
  echo "adb remount -R returned a nonzero status; retrying explicit remount..."
  adb remount
fi

echo "[3/6] Creating privileged-app directories..."
adb shell mkdir -p /system_ext/priv-app/SecureRenderingSystemLab
adb shell mkdir -p /system_ext/etc/permissions

echo "[4/6] Pushing platform-signed system lab..."
adb push "$APK" /system_ext/priv-app/SecureRenderingSystemLab/SecureRenderingSystemLab.apk
adb push "$PERMS" /system_ext/etc/permissions/privapp-permissions-com.tomex.securerenderlab.system.xml

adb shell chmod 0644 /system_ext/priv-app/SecureRenderingSystemLab/SecureRenderingSystemLab.apk
adb shell chmod 0644 /system_ext/etc/permissions/privapp-permissions-com.tomex.securerenderlab.system.xml
adb shell restorecon -RF /system_ext/priv-app/SecureRenderingSystemLab || true
adb shell restorecon -F /system_ext/etc/permissions/privapp-permissions-com.tomex.securerenderlab.system.xml || true

echo "[5/6] Rebooting so PackageManager scans it as a privileged system app..."
adb reboot
adb wait-for-device

echo "[6/6] Verifying grants..."
adb shell dumpsys package com.tomex.securerenderlab.system | grep -E   "pkgFlags|CAPTURE_SECURE_VIDEO_OUTPUT|CAPTURE_VIDEO_OUTPUT|CAPTURE_BLACKOUT_CONTENT|READ_FRAME_BUFFER" || true

echo
echo "Launch with:"
echo "  adb shell am start -n com.tomex.securerenderlab.system/.SystemLabActivity"
echo
echo "If CAPTURE_SECURE_VIDEO_OUTPUT is granted, run the secure-display self-test in the app."
