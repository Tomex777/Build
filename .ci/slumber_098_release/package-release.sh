#!/usr/bin/env bash
set -euo pipefail

SRC="${1:-/tmp/slumber-088}"
OUT="${2:-/tmp/slumber-release-out}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
rm -rf "$OUT"
mkdir -p "$OUT"

gradle -p "$SRC" --stacktrace bundleRelease assembleRelease

UNSIGNED="$SRC/app/build/outputs/apk/release/app-release-unsigned.apk"
AAB="$SRC/app/build/outputs/bundle/release/app-release.aab"
test -s "$UNSIGNED"
test -s "$AAB"

# The repository intentionally does not contain a production private key.
# Sign a release-mode QA APK with an ephemeral CI key so the exact release
# binary can be installed and exercised. The AAB remains the production
# artifact for store-side / owner-controlled signing.
QA_KEYSTORE="$OUT/slumber-release-qa.jks"
keytool -genkeypair -noprompt \
  -keystore "$QA_KEYSTORE" \
  -storepass slumber-release-qa \
  -keypass slumber-release-qa \
  -alias slumber \
  -keyalg RSA -keysize 4096 -validity 3650 \
  -dname "CN=Slumber Release QA,O=Slumber,C=NG" >/dev/null 2>&1

ALIGNED="$OUT/Slumber-0.8.8-release-aligned.apk"
SIGNED="$OUT/Slumber-0.8.8-release-arm64-compatible.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$QA_KEYSTORE" \
  --ks-key-alias slumber \
  --ks-pass pass:slumber-release-qa \
  --key-pass pass:slumber-release-qa \
  --out "$SIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED" > "$OUT/apksigner-verify.txt"

cp "$AAB" "$OUT/Slumber-0.8.8-release.aab"
"$BUILD_TOOLS/aapt" dump badging "$SIGNED" > "$OUT/release-badging.txt"
grep -q "package: name='com.night.pianohub'" "$OUT/release-badging.txt"
grep -q "sdkVersion:'26'" "$OUT/release-badging.txt"
grep -q "targetSdkVersion:'36'" "$OUT/release-badging.txt"

# Slumber currently ships no native shared objects, so this release APK is
# ABI-neutral and directly installable on ARM64 devices such as the target
# Galaxy A16 without bundling incompatible x86 native code.
if unzip -Z1 "$SIGNED" | grep -q '^lib/.*\.so$'; then
  if unzip -Z1 "$SIGNED" | grep '^lib/.*\.so$' | grep -v '^lib/arm64-v8a/' > "$OUT/non-arm64-native.txt"; then
    echo "Release contains non-ARM64 native libraries" >&2
    cat "$OUT/non-arm64-native.txt" >&2
    exit 1
  fi
  echo "ARM64_NATIVE_ONLY=1" > "$OUT/abi-contract.txt"
else
  echo "ABI_NEUTRAL_ARM64_COMPATIBLE=1" > "$OUT/abi-contract.txt"
fi

rm -f "$ALIGNED" "$QA_KEYSTORE"
sha256sum "$SIGNED" "$OUT/Slumber-0.8.8-release.aab" > "$OUT/release-sha256.txt"
cat > "$OUT/RELEASE_STATUS.txt" <<'TXT'
RELEASE MODE APK = BUILT
RELEASE APK SIGNATURE = QA INSTALL SIGNATURE
PRODUCTION AAB = BUILT
SDK CONTRACT = compile 36 / target 36 / min 26
ARM64 CONTRACT = COMPATIBLE
TXT
cat "$OUT/RELEASE_STATUS.txt"
