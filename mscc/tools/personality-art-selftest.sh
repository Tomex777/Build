#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/images" "$test_root/state"
# Synthetic assets: validate importer and dynamic profile selection without
# committing user's private image bytes to the test repository.
ffmpeg -hide_banner -loglevel error -f lavfi -i color=c=blue:s=1024x1280 -frames:v 1 -y "$test_root/images/hd.jpg" </dev/null
ffmpeg -hide_banner -loglevel error -f lavfi -i color=c=red:s=128x160 -frames:v 1 -y "$test_root/images/tiny.jpg" </dev/null

python3 - "$test_root" <<'PY'
import sys
from pathlib import Path
from zipfile import ZipFile
base=Path(sys.argv[1])
with ZipFile(base/'valid.zip','w') as zip:
  zip.write(base/'images/hd.jpg','nami/menu/nami-test.jpg')
with ZipFile(base/'tiny.zip','w') as zip:
  zip.write(base/'images/tiny.jpg','mimi/menu/mimi-too-small.jpg')
with ZipFile(base/'unsafe.zip','w') as zip:
  zip.write(base/'images/hd.jpg','../auth/creds.json')
PY

MSCC_PERSONALITY_ART_DIR="$test_root/state" bash tools/install-personality-art.sh "$test_root/valid.zip"
test -s "$test_root/state/nami/menu/nami-test.jpg"

MSCC_PERSONALITY_ART_DIR="$test_root/state" node --input-type=module <<'JS'
import assert from 'node:assert/strict'
import { chooseProfileAsset, readProfileAsset } from './profile-presentation.js'
const root=process.env.MSCC_PERSONALITY_ART_DIR
for (const kind of ['menu','intro']) {
  const asset=await chooseProfileAsset('nami', kind, { returning:true })
  assert(asset?.startsWith(root), 'HD Nami art must override bundled thumbnails for '+kind)
  assert(asset?.endsWith('/nami/menu/nami-test.jpg'))
  assert((await readProfileAsset(asset)).length>1000)
}
assert((await chooseProfileAsset('josiah','menu')).includes('/assets/personalities/'),
  'Other profiles should retain bundled fallback until HD art is uploaded')
JS

if MSCC_PERSONALITY_ART_DIR="$test_root/state" bash tools/install-personality-art.sh "$test_root/tiny.zip" >"$test_root/out" 2>&1; then
  echo 'ERROR: low-resolution art was accepted' >&2
  exit 1
fi
test ! -e "$test_root/state/mimi/menu/mimi-too-small.jpg"
if MSCC_PERSONALITY_ART_DIR="$test_root/state" bash tools/install-personality-art.sh "$test_root/unsafe.zip" >"$test_root/out" 2>&1; then
  echo 'ERROR: ZIP traversal was accepted' >&2
  exit 1
fi
test ! -e "$test_root/auth/creds.json"
echo 'PASS persistent HD personality art, intro fallback, corrupt/low-resolution rejection, and ZIP path safety'
