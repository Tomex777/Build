#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "Run this installer as root." >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 24 must be installed first." >&2
  exit 1
fi

NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
if [ "$NODE_MAJOR" -lt 24 ]; then
  echo "Cortex Agent requires Node.js 24 or newer; found $(node -v)." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y zip unzip

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install -d -m 0755 /opt/cortex-agent /opt/night /var/lib/cortex /var/lib/cortex/backups
install -m 0644 "$SCRIPT_DIR/index.js" /opt/cortex-agent/index.js
install -m 0644 "$SCRIPT_DIR/package.json" /opt/cortex-agent/package.json
install -m 0644 "$SCRIPT_DIR/cortex-agent.service" /etc/systemd/system/cortex-agent.service

if [ ! -f /etc/cortex-agent.env ]; then
  umask 077
  TOKEN="$(openssl rand -hex 32)"
  cat >/etc/cortex-agent.env <<EOF
CORTEX_AGENT_TOKEN=$TOKEN
NIGHT_ROOT=/opt/night
NIGHT_SERVICE=night.service
NIGHT_ENTRY=index.js
NIGHT_START_COMMAND=node index.js
CORTEX_STATE_DIR=/var/lib/cortex
HOST=127.0.0.1
PORT=47831
EOF
  chmod 0600 /etc/cortex-agent.env
  echo
  echo "Cortex Agent token (store this in Cortex):"
  echo "$TOKEN"
  echo
else
  grep -q '^CORTEX_STATE_DIR=' /etc/cortex-agent.env || echo 'CORTEX_STATE_DIR=/var/lib/cortex' >>/etc/cortex-agent.env
  echo "Keeping existing /etc/cortex-agent.env"
fi

systemctl daemon-reload
systemctl enable --now cortex-agent.service
systemctl --no-pager --full status cortex-agent.service || true

echo
echo "Cortex Agent is listening on 127.0.0.1:47831."
echo "Put it behind HTTPS before connecting the Android app."
