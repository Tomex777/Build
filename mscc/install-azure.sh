#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

VERSION="1.8.5"
BASE="/opt/mscc"
RELEASE="$BASE/releases/$VERSION"
STATE="/var/lib/mscc"
BACKUPS="/var/backups/mscc"
ENV_FILE="/etc/mscc.env"
ENV_EXAMPLE="/etc/mscc.env.example"
SERVICE="/etc/systemd/system/mscc.service"
ARCHIVE="/tmp/mscc-azure.tar.gz"
UNPACK="/tmp/mscc-azure-src"

echo "=== MSCC Azure install v$VERSION ==="

sudo mkdir -p "$RELEASE" "$STATE/auth" "$STATE/auth-b" "$STATE/data" "$BACKUPS"
sudo chown -R "$USER:$USER" "$BASE" "$STATE" "$BACKUPS"
chmod 700 "$STATE" "$STATE/auth" "$STATE/auth-b" "$STATE/data"

echo ">>> Downloading complete MSCC source from GitHub..."
rm -rf "$UNPACK" "$ARCHIVE"
curl -fsSL "https://github.com/Tomex777/Build/archive/refs/heads/mscc-azure.tar.gz" -o "$ARCHIVE"
mkdir -p "$UNPACK"
tar -xzf "$ARCHIVE" -C "$UNPACK"
rm -rf "$RELEASE"
mkdir -p "$RELEASE"
cp -a "$UNPACK/Build-mscc-azure/mscc/." "$RELEASE/"
rm -rf "$UNPACK" "$ARCHIVE" "$RELEASE/dist"

echo ">>> Installing Node dependencies..."
cd "$RELEASE"
npm install --omit=dev --no-audit --no-fund
npm run check

echo ">>> Activating release..."
ln -sfn "$RELEASE" "$BASE/current"

echo ">>> Installing environment template..."
sudo cp "$RELEASE/.env.example" "$ENV_EXAMPLE"
sudo chmod 600 "$ENV_EXAMPLE"

if [ ! -f "$ENV_FILE" ]; then
  sudo cp "$ENV_EXAMPLE" "$ENV_FILE"
  sudo chmod 600 "$ENV_FILE"
  echo "Created $ENV_FILE from template."
else
  echo "Existing $ENV_FILE preserved."
fi

echo ">>> Installing systemd service..."
sudo tee "$SERVICE" >/dev/null <<EOF
[Unit]
Description=MSCC Private WhatsApp Companion
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$USER
Group=$USER
WorkingDirectory=$BASE/current
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/node --max-old-space-size=192 $BASE/current/index.js
Restart=on-failure
RestartSec=4
KillSignal=SIGINT
TimeoutStopSec=20
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable mscc.service >/dev/null

A="$(sudo sed -n 's/^ACCOUNT_A_NUMBER=//p' "$ENV_FILE" | tail -1 | tr -d '\r' || true)"
PW="$(sudo sed -n 's/^WEB_PASSWORD=//p' "$ENV_FILE" | tail -1 | tr -d '\r' || true)"

if [[ "$A" =~ ^[0-9]{7,15}$ ]] && [ -n "$PW" ] && [ "$PW" != "change-this-password" ]; then
  echo ">>> Configuration looks ready; starting MSCC..."
  sudo systemctl restart mscc.service
  sleep 4
  sudo systemctl --no-pager --full status mscc.service || true
  echo
  curl -fsS http://127.0.0.1:8787/health || true
  echo
  echo "Local Cortex pairing bridge:"
  curl -fsS http://127.0.0.1:8788/state || true
  echo
else
  echo
  echo "MSCC code is installed, but the private configuration still needs to be migrated."
  echo "Service was enabled but NOT started."
  sudo systemctl stop mscc.service >/dev/null 2>&1 || true
fi

echo
echo "=== MSCC INSTALL COMPLETE ==="
echo "Release: $RELEASE"
echo "Current: $BASE/current"
echo "Commands: $BASE/current/commands"
echo "State A: $STATE/auth"
echo "State B: $STATE/auth-b"
echo "Data: $STATE/data"
echo "Environment: $ENV_FILE"
echo "Coturn remains: $(systemctl is-active coturn 2>/dev/null || true)"
