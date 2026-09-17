#!/usr/bin/env bash
set -euo pipefail

# Offline evidence collector for the exact WhatsApp / Instagram build that will be
# analyzed before Night Core marks an adapter SUPPORTED. This script never guesses
# hook classes and never modifies the APK.
#
# Usage:
#   bash nightmods/target-analysis/inspect-target-apk.sh /path/to/base.apk [output-dir]
#
# For split installs, point this at the extracted base.apk. Keep the split APKs next
# to it and include their SHA-256 values in the same analysis handoff.

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 /path/to/base.apk [output-dir]" >&2
  exit 2
fi

APK="$(realpath "$1")"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 2
fi

OUT="${2:-target-analysis-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT"
OUT="$(realpath "$OUT")"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must point to an Android SDK." >&2
  exit 2
fi

find_build_tool() {
  local name="$1"
  local candidate
  candidate="$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$name" -print 2>/dev/null | sort -V | tail -n 1)"
  if [[ -z "$candidate" || ! -x "$candidate" ]]; then
    echo "Android build tool not found: $name" >&2
    exit 2
  fi
  printf '%s\n' "$candidate"
}

AAPT="$(find_build_tool aapt)"
APKSIGNER="$(find_build_tool apksigner)"

BADGING="$OUT/badging.txt"
SIGNING="$OUT/signing.txt"
MANIFEST="$OUT/manifest-xmltree.txt"
RESOURCES="$OUT/resources.txt"
FILES="$OUT/files.txt"
DEX_STRINGS="$OUT/dex-strings-interesting.txt"
SUMMARY="$OUT/summary.txt"

"$AAPT" dump badging "$APK" > "$BADGING"
PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$BADGING" | head -n 1)"
VERSION_CODE="$(sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p" "$BADGING" | head -n 1)"
VERSION_NAME="$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" "$BADGING" | head -n 1)"

case "$PACKAGE" in
  com.whatsapp|com.instagram.android) ;;
  *)
    echo "Refusing target analysis for unexpected package: ${PACKAGE:-unknown}" >&2
    exit 3
    ;;
esac

sha256sum "$APK" > "$OUT/SHA256SUMS"
"$APKSIGNER" verify --print-certs "$APK" > "$SIGNING" 2>&1
"$AAPT" dump xmltree "$APK" AndroidManifest.xml > "$MANIFEST"
"$AAPT" dump resources "$APK" > "$RESOURCES"
unzip -Z1 "$APK" | sort > "$FILES"

# Pull semantic strings from every classes*.dex without pretending they are stable
# hook points. This is triage material only; the exact classes/methods still need to
# be verified in a decompiler against this same APK hash.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
: > "$DEX_STRINGS"
while IFS= read -r dex; do
  [[ "$dex" =~ ^classes([0-9]+)?\.dex$ ]] || continue
  unzip -p "$APK" "$dex" > "$TMP/$dex"
  {
    echo "===== $dex ====="
    strings -a -n 4 "$TMP/$dex" \
      | grep -Ei 'bubble|message|conversation|chat|rounded|corner|radius|spacing|instagram|whatsapp' \
      | sort -u \
      | head -n 500 || true
    echo
  } >> "$DEX_STRINGS"
done < "$FILES"

CERT_SHA256="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$SIGNING" | head -n 1)"
APK_SHA256="$(cut -d' ' -f1 "$OUT/SHA256SUMS")"

cat > "$SUMMARY" <<EOF
Night Core exact target analysis fingerprint
===========================================
package=$PACKAGE
versionName=$VERSION_NAME
versionCode=$VERSION_CODE
apkSha256=$APK_SHA256
signerCertSha256=$CERT_SHA256
sourceApk=$APK

Safety status
-------------
This report is evidence only.
Do not mark the adapter SUPPORTED from version strings alone.
Before adding a hook, verify the exact target classes/methods/resources in a decompiler
against apkSha256 above and keep unknown versions as ANALYSIS_REQUIRED.

Collected evidence
------------------
badging.txt
signing.txt
manifest-xmltree.txt
resources.txt
files.txt
dex-strings-interesting.txt
SHA256SUMS
EOF

cat "$SUMMARY"
