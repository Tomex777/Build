#!/usr/bin/env bash
set -euo pipefail

# sdkmanager sometimes exits after a corrupt/truncated system-image ZIP download.
# Prefetch and verify the image BEFORE android-emulator-runner starts, so a bad
# download can be retried without hiding or skipping any emulator tests.
api="${1:?Android API level is required}"
case "$api" in
  26|36) ;;
  *) echo "::error::Unexpected Android API level: $api" >&2; exit 2 ;;
esac

sdk_root="${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
package="system-images;android-${api};google_apis;x86_64"
image_dir="$sdk_root/system-images/android-${api}/google_apis/x86_64"

for attempt in 1 2 3; do
  echo "Checking emulator system image $package (attempt $attempt/3)"
  if sdkmanager --install "$package" && \
      test -s "$image_dir/system.img" && \
      test -s "$image_dir/ramdisk.img" && \
      test -s "$image_dir/source.properties"; then
    echo "Verified Android $api Google APIs x86_64 emulator image."
    exit 0
  fi

  if [ "$attempt" -eq 3 ]; then
    echo "::error::Android $api emulator system image failed installation or integrity checks after 3 attempts."
    exit 1
  fi

  echo "::warning::Incomplete/corrupt Android $api emulator system image; clearing only this image and retrying."
  rm -rf "$image_dir" "$HOME/.android/cache"
  sleep $((attempt * 6))
done
