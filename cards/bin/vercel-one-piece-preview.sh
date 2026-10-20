#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
node bin/seed-one-piece.mjs
mkdir -p .cache/browser
curl --fail --location --retry 3   https://storage.googleapis.com/chrome-for-testing-public/131.0.6778.204/linux64/chrome-headless-shell-linux64.zip   -o .cache/browser/chrome.zip
unzip -q .cache/browser/chrome.zip -d .cache/browser
chmod +x .cache/browser/chrome-headless-shell-linux64/chrome-headless-shell
export NIGHT_BROWSER_PATH="$PWD/.cache/browser/chrome-headless-shell-linux64/chrome-headless-shell"
npm run build -- --series one-piece --width 1000 --out dist/one-piece
