#!/usr/bin/env bash
# Optional, isolated extras for .removebg, .getexif and .ssweb.
# Run only after the Night deployment is healthy. Does not modify its systemd service.
set -euo pipefail

TOOLS_HOME="${MSCC_EXTRA_MEDIA_HOME:-/opt/mscc/tools}"
mkdir -p "$TOOLS_HOME"

echo '>>> Installing EXIF metadata reader and local image AI Python prerequisites'
sudo apt-get update -qq
sudo apt-get install -y --no-install-recommends \
  libimage-exiftool-perl python3-venv python3-pip ffmpeg unzip \
  libnss3 libatk1.0-0 libatk-bridge2.0-0 libgbm1 libx11-xcb1 libxcomposite1 \
  libxdamage1 libxfixes3 libxrandr2 libasound2t64

if [ "${MSCC_INSTALL_REMBG:-1}" = '1' ]; then
  echo '>>> Installing isolated rembg CPU environment (can be a large download)'
  python3 -m venv "$TOOLS_HOME/rembg-venv"
  "$TOOLS_HOME/rembg-venv/bin/python" -m pip install --disable-pip-version-check --upgrade pip
  "$TOOLS_HOME/rembg-venv/bin/python" -m pip install --disable-pip-version-check 'rembg[cpu,cli]'
  test -x "$TOOLS_HOME/rembg-venv/bin/rembg"
  echo "REMBG_PATH=$TOOLS_HOME/rembg-venv/bin/rembg"
  echo 'Note: first background-removal request may download the u2netp model.'
fi

if [ "${MSCC_INSTALL_BROWSER:-1}" = '1' ]; then
  echo '>>> Installing isolated Chrome for Testing (can be a large download)'
  mkdir -p "$TOOLS_HOME/browser-cache"
  npx --yes @puppeteer/browsers@latest install chrome@stable --path "$TOOLS_HOME/browser-cache"
  CHROME="$(find "$TOOLS_HOME/browser-cache/chrome" -type f -path '*/chrome-linux64/chrome' | sort | tail -1)"
  if [ -z "$CHROME" ] || [ ! -x "$CHROME" ]; then
    echo 'Chrome executable could not be located. Set SSWEB_CHROME_PATH manually.' >&2
    exit 1
  fi
  ln -sfn "$CHROME" "$TOOLS_HOME/chromium"
  echo "SSWEB_CHROME_PATH=$TOOLS_HOME/chromium"
fi

echo '>>> Set SSWEB_ALLOWED_HOSTS in /etc/mscc.env before enabling website captures.'
echo '>>> Rebooting/restarting existing MSCC services is not performed by this script.'
