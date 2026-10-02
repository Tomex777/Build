#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

VERSION="2.3.0"
SOURCE_REF="${MSCC_SOURCE_REF:-mscc-azure}"
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
echo "Source ref: $SOURCE_REF"

sudo mkdir -p "$RELEASE" "$STATE/auth" "$STATE/auth-b" "$STATE/data" "$BACKUPS"
sudo chown -R "$USER:$USER" "$BASE" "$STATE" "$BACKUPS"
chmod 700 "$STATE" "$STATE/auth" "$STATE/auth-b" "$STATE/data"

echo ">>> Downloading complete MSCC source from GitHub..."
rm -rf "$UNPACK" "$ARCHIVE"
curl -fsSL "https://github.com/Tomex777/Build/archive/$SOURCE_REF.tar.gz" -o "$ARCHIVE"
mkdir -p "$UNPACK"
tar -xzf "$ARCHIVE" -C "$UNPACK" --strip-components=1
rm -rf "$RELEASE"
mkdir -p "$RELEASE"
cp -a "$UNPACK/mscc/." "$RELEASE/"
rm -rf "$UNPACK" "$ARCHIVE" "$RELEASE/dist"

echo ">>> Installing Node dependencies..."
cd "$RELEASE"
npm install --omit=dev --no-audit --no-fund
npm run check

SOURCE_STACK_DIR="${MSCC_SOURCE_STACK_DIR:-/opt/mscc-source-stack}"
SOURCE_STACK_MARKER="$SOURCE_STACK_DIR/.animepahe-stack-version"
SOURCE_STACK_VERSION="3"
CURRENT_SOURCE_STACK_VERSION="$(cat "$SOURCE_STACK_MARKER" 2>/dev/null || true)"
if [ "$CURRENT_SOURCE_STACK_VERSION" != "$SOURCE_STACK_VERSION" ]; then
  echo ">>> Installing/upgrading AnimePahe Tor + FlareSolverr source stack..."
  bash "$RELEASE/tools/install-flaresolverr-tor.sh"
else
  echo ">>> AnimePahe source stack v$SOURCE_STACK_VERSION already installed."
fi

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

upsert_env() {
  local key="$1" value="$2"
  if sudo grep -q "^$key=" "$ENV_FILE"; then
    sudo sed -i "s|^$key=.*|$key=$value|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" | sudo tee -a "$ENV_FILE" >/dev/null
  fi
}

echo ">>> Hardening private control configuration..."
WEB_PASSWORD_VALUE="$(sudo sed -n 's/^WEB_PASSWORD=//p' "$ENV_FILE" | tail -1 | tr -d '\r' || true)"
if [ -z "$WEB_PASSWORD_VALUE" ] || [ "$WEB_PASSWORD_VALUE" = "change-this-password" ]; then
  upsert_env WEB_PASSWORD "$(openssl rand -hex 24)"
fi
WEB_SESSION_VALUE="$(sudo sed -n 's/^WEB_SESSION_SECRET=//p' "$ENV_FILE" | tail -1 | tr -d '\r' || true)"
if [ -z "$WEB_SESSION_VALUE" ]; then
  upsert_env WEB_SESSION_SECRET "$(openssl rand -hex 32)"
fi
upsert_env MSCC_WEB_HOST "127.0.0.1"
sudo chmod 600 "$ENV_FILE"

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

echo ">>> Starting MSCC..."
sudo systemctl restart mscc.service

READY=0
for _ in $(seq 1 20); do
  if curl -fsS http://127.0.0.1:8787/health >/tmp/mscc-health.json 2>/dev/null; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "MSCC did not become healthy." >&2
  sudo systemctl --no-pager --full status mscc.service || true
  sudo journalctl -u mscc.service -n 80 --no-pager || true
  exit 1
fi

sudo systemctl --no-pager --full status mscc.service || true
echo
cat /tmp/mscc-health.json
echo
echo "Local Cortex pairing bridge:"
curl -fsS http://127.0.0.1:8788/state
echo

echo
echo "=== MSCC INSTALL COMPLETE ==="
echo "Release: $RELEASE"
echo "Current: $BASE/current"
echo "Private commands: $BASE/current/private-commands"
echo "Public commands: $BASE/current/commands"
echo "State A: $STATE/auth"
echo "State B: $STATE/auth-b"
echo "Data: $STATE/data"
echo "Shared SQLite: $STATE/data/mscc-shared.sqlite"
echo "Environment: $ENV_FILE"
echo "Coturn remains: $(systemctl is-active coturn 2>/dev/null || true)"
