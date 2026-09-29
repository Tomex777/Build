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
ARM64_UNSIGNED="$OUT/arm64-release-unsigned.apk"
cp "$UNSIGNED" "$ARM64_UNSIGNED"

# The hosted Android 16 emulator is x86_64. Build the exact same optimized
# release variant for that emulator only; keep it outside the user artifacts.
rm -rf "$SRC/app/build/outputs/apk/release"
gradle -p "$SRC" --stacktrace assembleRelease -PslumberReleaseAbi=x86_64
X86_64_UNSIGNED="$SRC/app/build/outputs/apk/release/app-release-unsigned.apk"
test -s "$X86_64_UNSIGNED"

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
SIGNED="$OUT/Slumber-0.8.8-release-arm64-v8a.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$ARM64_UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$QA_KEYSTORE" \
  --ks-key-alias slumber \
  --ks-pass pass:slumber-release-qa \
  --key-pass pass:slumber-release-qa \
  --out "$SIGNED" "$ALIGNED"
QA_X86_64="/tmp/slumber-release-qa-x86_64.apk"
QA_ALIGNED="$OUT/Slumber-0.8.8-release-x86_64-qa-aligned.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$X86_64_UNSIGNED" "$QA_ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$QA_KEYSTORE" \
  --ks-key-alias slumber \
  --ks-pass pass:slumber-release-qa \
  --key-pass pass:slumber-release-qa \
  --out "$QA_X86_64" "$QA_ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED" > "$OUT/apksigner-verify.txt"

cp "$AAB" "$OUT/Slumber-0.8.8-release.aab"
"$BUILD_TOOLS/aapt" dump badging "$SIGNED" > "$OUT/release-badging.txt"
grep -q "package: name='com.night.pianohub'" "$OUT/release-badging.txt"
grep -q "sdkVersion:'26'" "$OUT/release-badging.txt"
grep -q "targetSdkVersion:'36'" "$OUT/release-badging.txt"

# Compose contributes androidx.graphics.path, which must remain packaged.
# Release abiFilters keeps its ARM64 implementation and excludes unrelated
# emulator/device ABIs from both the installable APK and the store AAB.
unzip -Z1 "$SIGNED" | grep '^lib/.*\.so$' > "$OUT/apk-native-libs.txt"
grep -q '^lib/arm64-v8a/.*\.so$' "$OUT/apk-native-libs.txt"
if grep -v '^lib/arm64-v8a/' "$OUT/apk-native-libs.txt" > "$OUT/non-arm64-native.txt"; then
  echo "Release APK contains non-ARM64 native libraries" >&2
  cat "$OUT/non-arm64-native.txt" >&2
  exit 1
fi

unzip -Z1 "$OUT/Slumber-0.8.8-release.aab" | grep '^base/lib/.*\.so$' > "$OUT/aab-native-libs.txt"
grep -q '^base/lib/arm64-v8a/.*\.so$' "$OUT/aab-native-libs.txt"
if grep -v '^base/lib/arm64-v8a/' "$OUT/aab-native-libs.txt" > "$OUT/aab-non-arm64-native.txt"; then
  echo "Release AAB contains non-ARM64 native libraries" >&2
  cat "$OUT/aab-non-arm64-native.txt" >&2
  exit 1
fi
echo "ARM64_NATIVE_ONLY=1" > "$OUT/abi-contract.txt"

rm -f "$ARM64_UNSIGNED" "$ALIGNED" "$QA_ALIGNED" "$QA_KEYSTORE"
sha256sum "$SIGNED" "$OUT/Slumber-0.8.8-release.aab" > "$OUT/release-sha256.txt"
cat > "$OUT/RELEASE_STATUS.txt" <<'TXT'
RELEASE MODE APK = BUILT
RELEASE APK SIGNATURE = QA INSTALL SIGNATURE
PRODUCTION AAB = BUILT
SDK CONTRACT = compile 36 / target 36 / min 26
ARM64 CONTRACT = COMPATIBLE
TXT
cat "$OUT/RELEASE_STATUS.txt"
