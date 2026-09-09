#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/app/src/main/assets/models/maia3-5m.fp16.onnx"
URL="https://huggingface.co/bqrio/maia3-onnx/resolve/f2582c005a63a034e493d93736ecdd6291dd82e7/maia3-5m.fp16.onnx?download=true"
mkdir -p "$(dirname "$OUT")"
if command -v curl >/dev/null 2>&1; then
  curl -L --fail --progress-bar "$URL" -o "$OUT"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$OUT" "$URL"
else
  echo "Install curl or wget, or download the model manually to: $OUT" >&2
  exit 1
fi
EXPECTED="ca22fc3031975932e693f9758149302efc177749165443ed52de828add8864fa"
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$OUT" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$OUT" | awk '{print $1}')
else
  echo "Model downloaded, but no SHA-256 utility was found. Expected: $EXPECTED"
  exit 0
fi
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "Model checksum mismatch. Expected $EXPECTED, got $ACTUAL" >&2
  rm -f "$OUT"
  exit 1
fi
echo "Maia-3 model installed and checksum verified at $OUT"
