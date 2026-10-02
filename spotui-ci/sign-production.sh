#!/usr/bin/env bash
set -euo pipefail
mode="${1:?universal or arm64 required}"
OUT=/tmp/spotui-artifacts
BT="$ANDROID_HOME/build-tools/36.0.0"
pin=2e0d59d39b494b5e1d8eb7ae62c9fc7ad67bad2f7b59e62d1c072feb2457d700
count=0
for name in LYRA_RELEASE_KEYSTORE_B64 LYRA_RELEASE_KEYSTORE_PASSWORD LYRA_RELEASE_KEY_ALIAS LYRA_RELEASE_KEY_PASSWORD; do
  if test -n "${!name:-}"; then count=$((count+1)); fi
done
if (( count == 0 )); then
  echo TEMPORARY_QA_ONLY > "$OUT/SIGNING-STATE.txt"
  if [[ "$mode" == universal ]]; then
    echo LYRA_RELEASE_APP_APK="$OUT/Lyra-universal-release-qa.apk" >> "$GITHUB_ENV"
    echo LYRA_RELEASE_SOURCE_APK="$OUT/Lyra-YouTube-Music-source-release-qa.apk" >> "$GITHUB_ENV"
  fi
  exit 0
fi
if (( count != 4 )); then echo 'Incomplete Lyra production signing secrets; refusing fallback' >&2; exit 1; fi
umask 077
key="$RUNNER_TEMP/lyra-release.jks"
trap 'rm -f "$key" "$RUNNER_TEMP/lyra-cert.der" "$RUNNER_TEMP/lyra-aligned.apk"' EXIT
printf '%s' "$LYRA_RELEASE_KEYSTORE_B64" | base64 --decode > "$key"
keytool -exportcert -keystore "$key" -alias "$LYRA_RELEASE_KEY_ALIAS" -storepass:env LYRA_RELEASE_KEYSTORE_PASSWORD -file "$RUNNER_TEMP/lyra-cert.der"
test "$(sha256sum "$RUNNER_TEMP/lyra-cert.der" | cut -d' ' -f1)" = "$pin"
sign_apk() {
  local input="$1" output="$2"
  test -s "$input"
  "$BT/zipalign" -f -P 16 4 "$input" "$RUNNER_TEMP/lyra-aligned.apk"
  "$BT/apksigner" sign --ks "$key" --ks-key-alias "$LYRA_RELEASE_KEY_ALIAS" --ks-pass env:LYRA_RELEASE_KEYSTORE_PASSWORD --key-pass env:LYRA_RELEASE_KEY_PASSWORD --out "$output" "$RUNNER_TEMP/lyra-aligned.apk"
  "$BT/apksigner" verify --verbose --print-certs "$output" > "$output.signature.txt"
  grep -q "certificate SHA-256 digest: $pin" "$output.signature.txt"
  "$BT/zipalign" -c -P 16 4 "$output"
}
if [[ "$mode" == universal ]]; then
  sign_apk "$OUT/Lyra-universal-release-unsigned.apk" "$OUT/Lyra-universal-production.apk"
  sign_apk "$OUT/Lyra-YouTube-Music-source-release-unsigned.apk" "$OUT/Lyra-YouTube-Music-source-production.apk"
  jarsigner -keystore "$key" -storepass:env LYRA_RELEASE_KEYSTORE_PASSWORD -keypass:env LYRA_RELEASE_KEY_PASSWORD -sigalg SHA256withRSA -digestalg SHA-256 -signedjar "$OUT/Lyra-production.aab" "$OUT/Lyra-release-unsigned.aab" "$LYRA_RELEASE_KEY_ALIAS"
  jarsigner -verify "$OUT/Lyra-production.aab" > "$OUT/AAB-VERIFICATION.txt"
  grep -q 'jar verified' "$OUT/AAB-VERIFICATION.txt"
  keytool -printcert -jarfile "$OUT/Lyra-production.aab" > "$OUT/AAB-CERTIFICATE.txt"
  grep -qi '2E:0D:59:D3:9B:49:4B:5E:1D:8E:B7:AE:62:C9:FC:7A:D6:7B:AD:2F:7B:59:E6:2D:1C:07:2F:EB:24:57:D7:00' "$OUT/AAB-CERTIFICATE.txt"
  echo LYRA_RELEASE_APP_APK="$OUT/Lyra-universal-production.apk" >> "$GITHUB_ENV"
  echo LYRA_RELEASE_SOURCE_APK="$OUT/Lyra-YouTube-Music-source-production.apk" >> "$GITHUB_ENV"
elif [[ "$mode" == arm64 ]]; then
  sign_apk "$OUT/Lyra-arm64-release-unsigned.apk" "$OUT/Lyra-arm64-production.apk"
  sign_apk "$OUT/Lyra-YouTube-Music-source-arm64-release-unsigned.apk" "$OUT/Lyra-YouTube-Music-source-arm64-production.apk"
  "$BT/aapt" dump badging "$OUT/Lyra-arm64-production.apk" > "$OUT/ARM64-BADGING.txt"
  grep -q "native-code: 'arm64-v8a'" "$OUT/ARM64-BADGING.txt"
  if grep '^native-code:' "$OUT/ARM64-BADGING.txt" | grep -Eq 'x86|armeabi'; then echo 'Unexpected ABI in ARM64 output' >&2; exit 1; fi
else exit 1; fi
echo PERMANENT_PRODUCTION_SIGNING > "$OUT/SIGNING-STATE.txt"
(cd "$OUT" && sha256sum *production.apk Lyra-production.aab > PRODUCTION-SHA256SUMS.txt)
