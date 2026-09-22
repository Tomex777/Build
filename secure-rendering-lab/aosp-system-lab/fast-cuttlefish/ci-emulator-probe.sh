#!/usr/bin/env bash
set -u

REPORT="${SYSTEMLAB_REPORT:-/tmp/systemlab-emulator-report.txt}"
APK="${SYSTEMLAB_APK:-$PWD/secure-rendering-system-lab-fast-cuttlefish/SecureRenderingSystemLab-platform.apk}"
PERMS="${SYSTEMLAB_PERMS:-$PWD/secure-rendering-system-lab-fast-cuttlefish/privapp-permissions-com.tomex.securerenderlab.system.xml}"
PKG="com.tomex.securerenderlab.system"

exec > >(tee "$REPORT") 2>&1

echo "Secure Rendering System Lab emulator probe"
echo "========================================="
echo

adb wait-for-device

echo "Build fingerprint: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
echo "Build type:        $(adb shell getprop ro.build.type | tr -d '\r')"
echo "ro.debuggable:     $(adb shell getprop ro.debuggable | tr -d '\r')"
echo "Build tags:        $(adb shell getprop ro.build.tags | tr -d '\r')"
echo

echo "--- adb root ---"
ROOT_OUT="$(adb root 2>&1)"
ROOT_STATUS=$?
printf '%s\n' "$ROOT_OUT"
adb wait-for-device

DEBUGGABLE="$(adb shell getprop ro.debuggable | tr -d '\r')"
if [[ "$ROOT_STATUS" -ne 0 || "$DEBUGGABLE" != "1" ]]; then
  echo
  echo "RESULT: SDK emulator image is not suitable for the privileged remount path."
  echo "Use the official AOSP Cuttlefish userdebug image instead."
  exit 0
fi

echo
echo "--- adb remount (emulator booted with -writable-system) ---"
REMOUNT_OUT="$(adb remount 2>&1)"
REMOUNT_STATUS=$?
printf '%s\n' "$REMOUNT_OUT"

if [[ "$REMOUNT_STATUS" -ne 0 ]]; then
  echo
  echo "RESULT: adb root works, but writable-system remount was rejected."
  echo "Falling back to official AOSP Cuttlefish userdebug is required for this path."
  exit 0
fi

echo
echo "--- reboot to activate disabled verity + overlayfs ---"
adb reboot
adb wait-for-device
adb root || true
adb wait-for-device

echo
echo "--- adb remount after reboot ---"
REMOUNT2_OUT="$(adb remount 2>&1)"
REMOUNT2_STATUS=$?
printf '%s\n' "$REMOUNT2_OUT"
if [[ "$REMOUNT2_STATUS" -ne 0 ]]; then
  echo "RESULT: second remount failed after reboot."
  exit 0
fi

echo
echo "--- verify system_ext is writable ---"
TEST_FILE="/system_ext/.secure_rendering_lab_write_test"
if ! adb shell "echo ok > $TEST_FILE" 2>/dev/null; then
  echo "RESULT: remount reported success but /system_ext is still not writable."
  exit 0
fi
adb shell rm -f "$TEST_FILE" || true
echo "/system_ext write test: SUCCESS"

echo
echo "--- push system app ---"
adb shell mkdir -p /system_ext/priv-app/SecureRenderingSystemLab
adb shell mkdir -p /system_ext/etc/permissions
adb push "$APK" /system_ext/priv-app/SecureRenderingSystemLab/SecureRenderingSystemLab.apk
adb push "$PERMS" /system_ext/etc/permissions/privapp-permissions-com.tomex.securerenderlab.system.xml
adb shell chmod 0644 /system_ext/priv-app/SecureRenderingSystemLab/SecureRenderingSystemLab.apk
adb shell chmod 0644 /system_ext/etc/permissions/privapp-permissions-com.tomex.securerenderlab.system.xml
adb shell restorecon -RF /system_ext/priv-app/SecureRenderingSystemLab || true
adb shell restorecon -F /system_ext/etc/permissions/privapp-permissions-com.tomex.securerenderlab.system.xml || true

echo
echo "--- reboot ---"
adb reboot
adb wait-for-device

echo "Waiting for Android boot completion..."
for i in $(seq 1 120); do
  BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$BOOTED" == "1" ]]; then
    break
  fi
  sleep 1
done
adb shell cmd package wait-for-handler 60000 >/dev/null 2>&1 || true

echo
echo "--- package path ---"
adb shell pm path "$PKG" || true

echo
echo "--- package grants ---"
adb shell dumpsys package "$PKG" | grep -E   "userId=|pkgFlags=|privateFlags=|CAPTURE_SECURE_VIDEO_OUTPUT|CAPTURE_VIDEO_OUTPUT|CAPTURE_BLACKOUT_CONTENT|READ_FRAME_BUFFER" || true

echo
echo "--- launcher resolution ---"
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  "$PKG" || true

echo
echo "--- launch + autorun secure-display self-test ---"
adb logcat -c || true
adb shell am start -W -n "$PKG/com.tomex.securerenderlab.system.SystemLabActivity" \
  --ez autorun true || true
sleep 6

echo
echo "--- Secure Rendering System Lab logs ---"
adb logcat -d -s SecureRenderingLabSys:I '*:S' || true

echo
echo "RESULT: completed privileged install + secure-display self-test probe."
