#!/usr/bin/env bash
set -euo pipefail

OUT="annie-android/app/build/outputs"
STATUS="$OUT/ANNIE-SIGNING-STATUS.txt"
mkdir -p "$OUT/production"
printf 'productionSigning=NOT_CONFIGURED\n' > "$STATUS"

configured=0
for name in ANNIE_RELEASE_KEYSTORE_B64 ANNIE_RELEASE_KEYSTORE_PASSWORD ANNIE_RELEASE_KEY_ALIAS ANNIE_RELEASE_KEY_PASSWORD; do
    if [ -n "${!name:-}" ]; then configured=$((configured + 1)); fi
done
if [ "$configured" -eq 0 ]; then
    echo '::notice::Permanent Annie signing secrets are not configured; no production release was created.'
    exit 0
fi
if [ "$configured" -ne 4 ]; then
    echo '::error::Permanent Annie signing configuration is incomplete.'
    exit 1
fi

APKSIGNER="$(command -v apksigner || true)"
if [ -z "$APKSIGNER" ]; then
    APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
fi
test -n "$APKSIGNER"
KEYSTORE="$(mktemp "${RUNNER_TEMP:-/tmp}/annie-production-XXXXXX.jks")"
trap 'rm -f "$KEYSTORE"' EXIT
chmod 600 "$KEYSTORE"
printf '%s' "$ANNIE_RELEASE_KEYSTORE_B64" | base64 --decode > "$KEYSTORE"

# This step only consumes the owner's persistent key. It never generates a key.
certificate="$OUT/production/ANNIE-CERTIFICATE.txt"
for abi in arm64-v8a universal; do
    apk="$OUT/apk/release/app-$abi-release-unsigned.apk"
    signed="$OUT/production/Annie-1.0.0-$abi-production-signed.apk"
    test -s "$apk"
    "$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$ANNIE_RELEASE_KEY_ALIAS" \
        --ks-pass env:ANNIE_RELEASE_KEYSTORE_PASSWORD --key-pass env:ANNIE_RELEASE_KEY_PASSWORD \
        --out "$signed" "$apk"
    verification="$("$APKSIGNER" verify --verbose --print-certs "$signed")"
    printf '%s\n' "$verification"
    digest="$(printf '%s\n' "$verification" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
    test "${#digest}" -eq 64
    if [ "$abi" = arm64-v8a ]; then
        printf '%s\n' "$digest" > "$certificate"
    else
        test "$digest" = "$(cat "$certificate")"
    fi
    if [ -n "${ANNIE_RELEASE_CERT_SHA256:-}" ]; then
        expected="$(printf '%s' "$ANNIE_RELEASE_CERT_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
        if [ "$digest" != "$expected" ]; then
            echo '::error::Annie production certificate differs from the pinned signing identity.'
            exit 1
        fi
    fi
done

aab="$OUT/production/Annie-1.0.0-production-signed.aab"
cp "$OUT/bundle/release/app-release.aab" "$aab"
jarsigner -keystore "$KEYSTORE" -storepass:env ANNIE_RELEASE_KEYSTORE_PASSWORD \
    -keypass:env ANNIE_RELEASE_KEY_PASSWORD -sigalg SHA256withRSA -digestalg SHA-256 \
    "$aab" "$ANNIE_RELEASE_KEY_ALIAS"
jarsigner -verify "$aab" | tee "$OUT/production/AAB-VERIFICATION.txt"
grep -Fq 'jar verified.' "$OUT/production/AAB-VERIFICATION.txt"
bundle_digest="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
test "$bundle_digest" = "$(cat "$certificate")"
(
    cd "$OUT/production"
    sha256sum ./*.apk ./*.aab > SHA256SUMS.txt
    sha256sum --check SHA256SUMS.txt
)
printf 'productionSigning=SIGNED\n' > "$STATUS"
