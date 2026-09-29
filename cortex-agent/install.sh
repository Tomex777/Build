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
apt-get install -y zip unzip acl sudo

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

AGENT_USER=cortex-agent
AGENT_GROUP=cortex-agent

if ! getent group "$AGENT_GROUP" >/dev/null 2>&1; then
  groupadd --system "$AGENT_GROUP"
fi
if ! id -u "$AGENT_USER" >/dev/null 2>&1; then
  useradd --system --gid "$AGENT_GROUP" --home-dir /var/lib/cortex --shell /usr/sbin/nologin "$AGENT_USER"
fi
if getent group systemd-journal >/dev/null 2>&1; then
  usermod -a -G systemd-journal "$AGENT_USER"
fi
install -d -m 0755 /opt/cortex-agent /opt/night
install -d -o "$AGENT_USER" -g "$AGENT_GROUP" -m 0750 /var/lib/cortex /var/lib/cortex/backups
install -d -m 0755 /usr/local/libexec
install -m 0644 "$SCRIPT_DIR/index.js" /opt/cortex-agent/index.js
install -m 0644 "$SCRIPT_DIR/package.json" /opt/cortex-agent/package.json
install -m 0644 "$SCRIPT_DIR/cortex-agent.service" /etc/systemd/system/cortex-agent.service
install -m 0755 "$SCRIPT_DIR/cortex-agent-control" /usr/local/libexec/cortex-agent-control

if [ ! -f /etc/cortex-agent.env ]; then
  umask 077
  TOKEN="$(openssl rand -hex 32)"
  cat >/etc/cortex-agent.env <<EOF
CORTEX_AGENT_TOKEN=$TOKEN
CORTEX_PROJECT_ROOT=/opt/mscc/current
CORTEX_SERVICE=mscc.service
CORTEX_ENTRY=index.js
CORTEX_START_COMMAND=node --max-old-space-size=192 index.js
CORTEX_GIT_REPO=Tomex777/Build
CORTEX_GIT_BRANCH=mscc-azure
CORTEX_STATE_DIR=/var/lib/cortex
CORTEX_COMMAND_SETTINGS_FILE=/var/lib/mscc/data/mscc-settings.json
CORTEX_COMMAND_SETTINGS_SCHEMA_FILE=/var/lib/mscc/data/cortex-settings-schema.json
CORTEX_PRIVATE_BACKUP_PATHS=/etc/mscc.env:/var/lib/mscc/auth:/var/lib/mscc/auth-b:/var/lib/mscc/data
HOST=127.0.0.1
PORT=47831
EOF
  chmod 0600 /etc/cortex-agent.env
  echo
  echo "Cortex Agent token (store this in Cortex):"
  echo "$TOKEN"
  echo
else
  grep -q '^CORTEX_PROJECT_ROOT=' /etc/cortex-agent.env || echo 'CORTEX_PROJECT_ROOT=/opt/mscc/current' >>/etc/cortex-agent.env
  grep -q '^CORTEX_SERVICE=' /etc/cortex-agent.env || echo 'CORTEX_SERVICE=mscc.service' >>/etc/cortex-agent.env
  grep -q '^CORTEX_ENTRY=' /etc/cortex-agent.env || echo 'CORTEX_ENTRY=index.js' >>/etc/cortex-agent.env
  grep -q '^CORTEX_START_COMMAND=' /etc/cortex-agent.env || echo 'CORTEX_START_COMMAND=node --max-old-space-size=192 index.js' >>/etc/cortex-agent.env
  grep -q '^CORTEX_GIT_REPO=' /etc/cortex-agent.env || echo 'CORTEX_GIT_REPO=Tomex777/Build' >>/etc/cortex-agent.env
  grep -q '^CORTEX_GIT_BRANCH=' /etc/cortex-agent.env || echo 'CORTEX_GIT_BRANCH=mscc-azure' >>/etc/cortex-agent.env
  grep -q '^CORTEX_STATE_DIR=' /etc/cortex-agent.env || echo 'CORTEX_STATE_DIR=/var/lib/cortex' >>/etc/cortex-agent.env
  grep -q '^CORTEX_COMMAND_SETTINGS_FILE=' /etc/cortex-agent.env || echo 'CORTEX_COMMAND_SETTINGS_FILE=/var/lib/mscc/data/mscc-settings.json' >>/etc/cortex-agent.env
  grep -q '^CORTEX_COMMAND_SETTINGS_SCHEMA_FILE=' /etc/cortex-agent.env || echo 'CORTEX_COMMAND_SETTINGS_SCHEMA_FILE=/var/lib/mscc/data/cortex-settings-schema.json' >>/etc/cortex-agent.env
  grep -q '^CORTEX_PRIVATE_BACKUP_PATHS=' /etc/cortex-agent.env || echo 'CORTEX_PRIVATE_BACKUP_PATHS=/etc/mscc.env:/var/lib/mscc/auth:/var/lib/mscc/auth-b:/var/lib/mscc/data' >>/etc/cortex-agent.env
  echo "Keeping existing /etc/cortex-agent.env"
fi

cat >/etc/sudoers.d/cortex-agent <<'EOF'
cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control start, /usr/local/libexec/cortex-agent-control stop, /usr/local/libexec/cortex-agent-control restart, /usr/local/libexec/cortex-agent-control enable, /usr/local/libexec/cortex-agent-control disable
EOF
chmod 0440 /etc/sudoers.d/cortex-agent
visudo -cf /etc/sudoers.d/cortex-agent >/dev/null

env_value() {
  local key="$1"
  sed -n "s/^$key=//p" /etc/cortex-agent.env | tail -n1
}

grant_rw_tree() {
  local target="$1"
  [ -e "$target" ] || return 0
  target="$(readlink -f "$target" 2>/dev/null || printf '%s' "$target")"
  setfacl -Rm "u:$AGENT_USER:rwX" "$target"
  if [ -d "$target" ]; then
    find "$target" -type d -exec setfacl -m "d:u:$AGENT_USER:rwX" {} +
  fi
}

grant_read_tree() {
  local target="$1"
  [ -e "$target" ] || return 0
  target="$(readlink -f "$target" 2>/dev/null || printf '%s' "$target")"
  setfacl -Rm "u:$AGENT_USER:rX" "$target"
  if [ -d "$target" ]; then
    find "$target" -type d -exec setfacl -m "d:u:$AGENT_USER:rX" {} +
  fi
}

PROJECT_ROOT="$(env_value CORTEX_PROJECT_ROOT)"
STATE_DIR="$(env_value CORTEX_STATE_DIR)"
SETTINGS_FILE="$(env_value CORTEX_COMMAND_SETTINGS_FILE)"
SCHEMA_FILE="$(env_value CORTEX_COMMAND_SETTINGS_SCHEMA_FILE)"
PRIVATE_PATHS="$(env_value CORTEX_PRIVATE_BACKUP_PATHS)"

[ -n "$PROJECT_ROOT" ] || PROJECT_ROOT=/opt/mscc/current
[ -n "$STATE_DIR" ] || STATE_DIR=/var/lib/cortex
[ -n "$SETTINGS_FILE" ] || SETTINGS_FILE=/var/lib/mscc/data/mscc-settings.json
[ -n "$SCHEMA_FILE" ] || SCHEMA_FILE=/var/lib/mscc/data/cortex-settings-schema.json

install -d -o "$AGENT_USER" -g "$AGENT_GROUP" -m 0750 "$STATE_DIR" "$STATE_DIR/backups"
grant_rw_tree "$PROJECT_ROOT"
grant_rw_tree "$(dirname "$SETTINGS_FILE")"
grant_read_tree "$SCHEMA_FILE"

IFS=':' read -r -a private_paths <<<"$PRIVATE_PATHS"
for private_path in "${private_paths[@]}"; do
  [ -n "$private_path" ] || continue
  parent="$(dirname "$private_path")"
  [ "$parent" = "/" ] || setfacl -m "u:$AGENT_USER:--x" "$parent" 2>/dev/null || true
  grant_read_tree "$private_path"
done

systemctl daemon-reload
systemctl enable --now cortex-agent.service
systemctl --no-pager --full status cortex-agent.service || true

echo
echo "Cortex Agent is listening on 127.0.0.1:47831."
echo "Put it behind HTTPS before connecting the Android app."
