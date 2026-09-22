#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 /path/to/aosp [--apply-diagnostics]" >&2
  exit 2
fi

AOSP_ROOT="$(cd "$1" && pwd)"
APPLY_DIAGNOSTICS=""
if [[ $# -ge 2 ]]; then
  APPLY_DIAGNOSTICS="$2"
fi
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEST="$AOSP_ROOT/packages/apps/SecureRenderingSystemLab"

rm -rf "$DEST"
mkdir -p "$DEST"
cp "$SCRIPT_DIR/Android.bp" "$DEST/"
cp "$SCRIPT_DIR/AndroidManifest.xml" "$DEST/"
cp "$SCRIPT_DIR/privapp-permissions-com.tomex.securerenderlab.system.xml" "$DEST/"
cp -R "$SCRIPT_DIR/src" "$DEST/"
cp -R "$SCRIPT_DIR/product" "$DEST/"

echo "Installed SecureRenderingSystemLab source at:"
echo "  $DEST"
echo
echo "Add this to your userdebug product makefile:"
echo '  $(call inherit-product-if-exists, packages/apps/SecureRenderingSystemLab/product/secure_rendering_system_lab.mk)'

if [[ "$APPLY_DIAGNOSTICS" == "--apply-diagnostics" ]]; then
  echo
  echo "Applying diagnostic-only framework patches..."
  git -C "$AOSP_ROOT/frameworks/native" apply "$SCRIPT_DIR/patches/0001-surfaceflinger-secure-capture-diagnostics.patch"
  git -C "$AOSP_ROOT/frameworks/base" apply "$SCRIPT_DIR/patches/0002-displaymanager-secure-display-diagnostics.patch"
  echo "Diagnostic patches applied."
fi

echo
echo "Build from the AOSP root after lunching a userdebug target:"
echo "  m SecureRenderingSystemLab"
echo
echo "After boot:"
echo "  adb shell dumpsys package com.tomex.securerenderlab.system"
echo "  adb logcat -s SurfaceFlinger DisplayManagerService"
echo
echo "The secure-display self-test is OWN_CONTENT_ONLY and never mirrors the physical display."
