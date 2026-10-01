#!/usr/bin/env bash
set -euo pipefail

INPUT="${1:?Unsigned release APK is required}"
OUTPUT="${2:?Signed output APK is required}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ENDLESS_RELEASE_KEYSTORE:?Private release keystore path is required}"
: "${ENDLESS_RELEASE_KEYSTORE_PASSWORD:?Keystore password is required}"
: "${ENDLESS_RELEASE_KEY_PASSWORD:?Key password is required}"
ENDLESS_RELEASE_KEY_ALIAS="${ENDLESS_RELEASE_KEY_ALIAS:-endless-release}"
APKSIGNER="${APKSIGNER:-apksigner}"
test -s "$INPUT"
test -s "$ENDLESS_RELEASE_KEYSTORE"
[[ "$INPUT" != "$OUTPUT" ]]
if "$APKSIGNER" verify "$INPUT" >/dev/null 2>&1; then
    echo "Expected an unsigned APK; refusing to replace an existing signing identity." >&2
    exit 1
fi

"$APKSIGNER" sign --ks "$ENDLESS_RELEASE_KEYSTORE" \
    --ks-key-alias "$ENDLESS_RELEASE_KEY_ALIAS" \
    --ks-pass env:ENDLESS_RELEASE_KEYSTORE_PASSWORD \
    --key-pass env:ENDLESS_RELEASE_KEY_PASSWORD \
    --min-sdk-version 26 --out "$OUTPUT" "$INPUT"
verification="$("$APKSIGNER" verify --verbose --print-certs "$OUTPUT")"
actual="$(printf '%s\n' "$verification" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')"
expected="$(tr -d '[:space:]' < "$ROOT/release/endless-release-sha256.txt")"
if [[ "$actual" != "$expected" ]]; then
    rm -f "$OUTPUT" "$OUTPUT.idsig"
    echo "Release certificate does not match Endless's permanent identity." >&2
    exit 1
fi
printf '%s\n' "$verification"
sha256sum "$OUTPUT"
