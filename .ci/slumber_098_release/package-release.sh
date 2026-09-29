#!/usr/bin/env bash
set -euo pipefail

SRC="${1:-/tmp/slumber-088}"
OUT="${2:-/tmp/slumber-release-out}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
rm -rf "$OUT"
mkdir -p "$OUT"

# Main release build: no ABI filter, so the direct-download APK and AAB carry
# every ABI provided by Slumber's native dependencies.
gradle -p "$SRC" --stacktrace bundleRelease assembleRelease
UNIVERSAL_UNSIGNED_SRC="$SRC/app/build/outputs/apk/release/app-release-unsigned.apk"
AAB_UNSIGNED_SRC="$SRC/app/build/outputs/bundle/release/app-release.aab"
test -s "$UNIVERSAL_UNSIGNED_SRC"
test -s "$AAB_UNSIGNED_SRC"
UNIVERSAL_UNSIGNED="$OUT/Slumber-0.8.8-universal-release-unsigned.apk"
AAB_UNSIGNED="$OUT/Slumber-0.8.8-release-unsigned.aab"
cp "$UNIVERSAL_UNSIGNED_SRC" "$UNIVERSAL_UNSIGNED"
cp "$AAB_UNSIGNED_SRC" "$AAB_UNSIGNED"

# Preserve the optimized ARM64 companion APK separately.
rm -rf "$SRC/app/build/outputs/apk/release"
gradle -p "$SRC" --stacktrace assembleRelease -PslumberReleaseAbi=arm64-v8a
ARM64_UNSIGNED_SRC="$SRC/app/build/outputs/apk/release/app-release-unsigned.apk"
test -s "$ARM64_UNSIGNED_SRC"
ARM64_UNSIGNED="$OUT/Slumber-0.8.8-arm64-v8a-release-unsigned.apk"
cp "$ARM64_UNSIGNED_SRC" "$ARM64_UNSIGNED"

# QA-only installable UNIVERSAL release. This key is generated per CI run and
# is never a portfolio signing identity.
QA_KEYSTORE="$RUNNER_TEMP/slumber-release-qa.jks"
keytool -genkeypair -noprompt   -keystore "$QA_KEYSTORE"   -storepass slumber-release-qa   -keypass slumber-release-qa   -alias slumber   -keyalg RSA -keysize 4096 -validity 3650   -dname "CN=Slumber Release QA,O=Slumber,C=NG" >/dev/null 2>&1

sign_apk_with_qa() {
  local input="$1"
  local output="$2"
  local aligned="$RUNNER_TEMP/$(basename "$output").aligned.apk"
  "$BUILD_TOOLS/zipalign" -f -p 4 "$input" "$aligned"
  "$BUILD_TOOLS/apksigner" sign     --ks "$QA_KEYSTORE"     --ks-key-alias slumber     --ks-pass pass:slumber-release-qa     --key-pass pass:slumber-release-qa     --out "$output" "$aligned"
  "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$output"
  rm -f "$aligned"
}
QA_UNIVERSAL="$OUT/Slumber-0.8.8-universal-release-QA.apk"
sign_apk_with_qa "$UNIVERSAL_UNSIGNED" "$QA_UNIVERSAL"
"$BUILD_TOOLS/apksigner" verify --print-certs "$QA_UNIVERSAL" > "$OUT/QA-SIGNATURE.txt"

# Package/version/debug-component verification is performed against the exact
# unsigned payload that may later receive the owner's permanent signature.
"$BUILD_TOOLS/aapt" dump badging "$UNIVERSAL_UNSIGNED" > "$OUT/release-badging.txt"
grep -q "package: name='com.night.pianohub'" "$OUT/release-badging.txt"
grep -q "versionCode='88'" "$OUT/release-badging.txt"
grep -q "versionName='0.8.8'" "$OUT/release-badging.txt"
grep -q "sdkVersion:'26'" "$OUT/release-badging.txt"
grep -q "targetSdkVersion:'36'" "$OUT/release-badging.txt"
"$SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer" manifest print "$UNIVERSAL_UNSIGNED" > "$OUT/release-manifest.xml"
if grep -Eq 'android:debuggable="true"|android:testOnly="true"' "$OUT/release-manifest.xml"; then
  echo "Debug/test-only flag leaked into Slumber release manifest" >&2
  exit 1
fi

unzip -Z1 "$UNIVERSAL_UNSIGNED" | grep '^lib/.*\.so$' > "$OUT/universal-native-libs.txt"
grep -q '^lib/arm64-v8a/' "$OUT/universal-native-libs.txt"
grep -q '^lib/x86_64/' "$OUT/universal-native-libs.txt"

unzip -Z1 "$ARM64_UNSIGNED" | grep '^lib/.*\.so$' > "$OUT/arm64-native-libs.txt"
grep -q '^lib/arm64-v8a/' "$OUT/arm64-native-libs.txt"
if grep -v '^lib/arm64-v8a/' "$OUT/arm64-native-libs.txt" > "$OUT/arm64-unexpected-native-libs.txt"; then
  echo "ARM64 optimized APK contains non-ARM64 native libraries" >&2
  cat "$OUT/arm64-unexpected-native-libs.txt" >&2
  exit 1
fi

unzip -Z1 "$AAB_UNSIGNED" | grep '^base/lib/.*\.so$' > "$OUT/aab-native-libs.txt"
grep -q '^base/lib/arm64-v8a/' "$OUT/aab-native-libs.txt"
grep -q '^base/lib/x86_64/' "$OUT/aab-native-libs.txt"

production_signed=false
if [ -n "${SLUMBER_RELEASE_KEYSTORE_B64:-}" ] &&
   [ -n "${SLUMBER_RELEASE_KEY_ALIAS:-}" ] &&
   [ -n "${SLUMBER_RELEASE_KEYSTORE_PASSWORD:-}" ] &&
   [ -n "${SLUMBER_RELEASE_KEY_PASSWORD:-}" ]; then
  KEYSTORE="$RUNNER_TEMP/slumber-owner-release.jks"
  printf '%s' "$SLUMBER_RELEASE_KEYSTORE_B64" | base64 --decode > "$KEYSTORE"
  sign_owner_apk() {
    local input="$1"
    local output="$2"
    local aligned="$RUNNER_TEMP/$(basename "$output").owner-aligned.apk"
    "$BUILD_TOOLS/zipalign" -f -p 4 "$input" "$aligned"
    "$BUILD_TOOLS/apksigner" sign       --ks "$KEYSTORE"       --ks-key-alias "$SLUMBER_RELEASE_KEY_ALIAS"       --ks-pass env:SLUMBER_RELEASE_KEYSTORE_PASSWORD       --key-pass env:SLUMBER_RELEASE_KEY_PASSWORD       --out "$output" "$aligned"
    "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$output"
    rm -f "$aligned"
  }
  sign_owner_apk "$UNIVERSAL_UNSIGNED" "$OUT/Slumber-0.8.8-universal-release.apk"
  sign_owner_apk "$ARM64_UNSIGNED" "$OUT/Slumber-0.8.8-arm64-v8a-release.apk"
  cp "$AAB_UNSIGNED" "$OUT/Slumber-0.8.8-release.aab"
  jarsigner     -keystore "$KEYSTORE"     -storepass "$SLUMBER_RELEASE_KEYSTORE_PASSWORD"     -keypass "$SLUMBER_RELEASE_KEY_PASSWORD"     -sigalg SHA256withRSA -digestalg SHA-256     "$OUT/Slumber-0.8.8-release.aab" "$SLUMBER_RELEASE_KEY_ALIAS"
  jarsigner -verify "$OUT/Slumber-0.8.8-release.aab"
  "$BUILD_TOOLS/apksigner" verify --print-certs "$OUT/Slumber-0.8.8-universal-release.apk" > "$OUT/PRODUCTION-SIGNATURE.txt"
  rm -f "$KEYSTORE"
  production_signed=true
fi

rm -f "$QA_KEYSTORE"
sha256sum "$OUT"/*.apk "$OUT"/*.aab | sort > "$OUT/SHA256SUMS.txt"
cat > "$OUT/RELEASE_STATUS.txt" <<TXT
APP=Slumber
PACKAGE=com.night.pianohub
VERSION_CODE=88
VERSION_NAME=0.8.8
SDK_CONTRACT=compile36-target36-min26
PORTFOLIO_APK=universal
UNIVERSAL_QA_INSTALLABLE=1
PRODUCTION_SIGNED=$([ "$production_signed" = true ] && echo 1 || echo 0)
SIGNING_STATUS=$([ "$production_signed" = true ] && echo OWNER_PERMANENT_KEY || echo OWNER_KEY_REQUIRED)
QA_SIGNATURE_PUBLIC_DISTRIBUTION=FORBIDDEN
TXT
cat "$OUT/RELEASE_STATUS.txt"
