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
RUNNER_USER=cortex-runner
RUNNER_GROUP=cortex-runner

if ! getent group "$AGENT_GROUP" >/dev/null 2>&1; then
  groupadd --system "$AGENT_GROUP"
fi
if ! id -u "$AGENT_USER" >/dev/null 2>&1; then
  useradd --system --gid "$AGENT_GROUP" --home-dir /var/lib/cortex --shell /usr/sbin/nologin "$AGENT_USER"
fi
if ! getent group "$RUNNER_GROUP" >/dev/null 2>&1; then
  groupadd --system "$RUNNER_GROUP"
fi
if ! id -u "$RUNNER_USER" >/dev/null 2>&1; then
  useradd --system --gid "$RUNNER_GROUP" --home-dir /var/lib/cortex-runner --shell /usr/sbin/nologin "$RUNNER_USER"
fi
if getent group systemd-journal >/dev/null 2>&1; then
  usermod -a -G systemd-journal "$AGENT_USER"
fi
install -d -m 0755 /opt/cortex-agent /opt/night
install -d -o "$AGENT_USER" -g "$AGENT_GROUP" -m 0750 /var/lib/cortex /var/lib/cortex/backups
install -d -o "$RUNNER_USER" -g "$RUNNER_GROUP" -m 0750 /var/lib/cortex-runner
install -d -o "$RUNNER_USER" -g "$AGENT_GROUP" -m 0750 /var/lib/cortex/runner
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
CORTEX_MAX_TRANSFER_BYTES=536870912
CORTEX_GIT_REPO=Tomex777/Build
CORTEX_GIT_BRANCH=mscc-azure
CORTEX_STATE_DIR=/var/lib/cortex
CORTEX_COMMAND_SETTINGS_FILE=/var/lib/mscc/data/mscc-settings.json
CORTEX_COMMAND_SETTINGS_SCHEMA_FILE=/var/lib/mscc/data/cortex-settings-schema.json
CORTEX_MSCC_ENV_FILE=/etc/mscc.env
CORTEX_ENV_SCHEMA_FILE=/var/lib/mscc/data/cortex-environment-schema.json
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
  grep -q '^CORTEX_MAX_TRANSFER_BYTES=' /etc/cortex-agent.env || echo 'CORTEX_MAX_TRANSFER_BYTES=536870912' >>/etc/cortex-agent.env
  grep -q '^CORTEX_GIT_REPO=' /etc/cortex-agent.env || echo 'CORTEX_GIT_REPO=Tomex777/Build' >>/etc/cortex-agent.env
  grep -q '^CORTEX_GIT_BRANCH=' /etc/cortex-agent.env || echo 'CORTEX_GIT_BRANCH=mscc-azure' >>/etc/cortex-agent.env
  grep -q '^CORTEX_STATE_DIR=' /etc/cortex-agent.env || echo 'CORTEX_STATE_DIR=/var/lib/cortex' >>/etc/cortex-agent.env
  grep -q '^CORTEX_COMMAND_SETTINGS_FILE=' /etc/cortex-agent.env || echo 'CORTEX_COMMAND_SETTINGS_FILE=/var/lib/mscc/data/mscc-settings.json' >>/etc/cortex-agent.env
  grep -q '^CORTEX_COMMAND_SETTINGS_SCHEMA_FILE=' /etc/cortex-agent.env || echo 'CORTEX_COMMAND_SETTINGS_SCHEMA_FILE=/var/lib/mscc/data/cortex-settings-schema.json' >>/etc/cortex-agent.env
  grep -q '^CORTEX_MSCC_ENV_FILE=' /etc/cortex-agent.env || echo 'CORTEX_MSCC_ENV_FILE=/etc/mscc.env' >>/etc/cortex-agent.env
  grep -q '^CORTEX_ENV_SCHEMA_FILE=' /etc/cortex-agent.env || echo 'CORTEX_ENV_SCHEMA_FILE=/var/lib/mscc/data/cortex-environment-schema.json' >>/etc/cortex-agent.env
  grep -q '^CORTEX_PRIVATE_BACKUP_PATHS=' /etc/cortex-agent.env || echo 'CORTEX_PRIVATE_BACKUP_PATHS=/etc/mscc.env:/var/lib/mscc/auth:/var/lib/mscc/auth-b:/var/lib/mscc/data' >>/etc/cortex-agent.env
  echo "Keeping existing /etc/cortex-agent.env"
fi

cat >/etc/sudoers.d/cortex-agent <<'EOF'
cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control start, /usr/local/libexec/cortex-agent-control stop, /usr/local/libexec/cortex-agent-control restart, /usr/local/libexec/cortex-agent-control enable, /usr/local/libexec/cortex-agent-control disable
cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control env-set *
cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control runner-start *
cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control runner-stop
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

grant_project_tree() {
  local target="$1"
  [ -d "$target" ] || {
    echo "Configured Cortex project root does not exist: $target" >&2
    echo "Install/configure MSCC first or set CORTEX_PROJECT_ROOT correctly." >&2
    exit 1
  }
  target="$(readlink -f "$target" 2>/dev/null || printf '%s' "$target")"
  find "$target" \
    \( -type d \( -name node_modules -o -name .git -o -name .ssh -o -name .gradle -o -name .cortex \) -prune \) -o \
    -exec setfacl -m "u:$AGENT_USER:rwX" {} +
  find "$target" \
    \( -type d \( -name node_modules -o -name .git -o -name .ssh -o -name .gradle -o -name .cortex \) -prune \) -o \
    -type d -exec setfacl -m "d:u:$AGENT_USER:rwX" {} +
}

PROJECT_ROOT="$(env_value CORTEX_PROJECT_ROOT)"
STATE_DIR="$(env_value CORTEX_STATE_DIR)"
SETTINGS_FILE="$(env_value CORTEX_COMMAND_SETTINGS_FILE)"
SCHEMA_FILE="$(env_value CORTEX_COMMAND_SETTINGS_SCHEMA_FILE)"
ENV_SCHEMA_FILE="$(env_value CORTEX_ENV_SCHEMA_FILE)"
PRIVATE_PATHS="$(env_value CORTEX_PRIVATE_BACKUP_PATHS)"

[ -n "$PROJECT_ROOT" ] || PROJECT_ROOT=/opt/mscc/current
[ -n "$STATE_DIR" ] || STATE_DIR=/var/lib/cortex
[ -n "$SETTINGS_FILE" ] || SETTINGS_FILE=/var/lib/mscc/data/mscc-settings.json
[ -n "$SCHEMA_FILE" ] || SCHEMA_FILE=/var/lib/mscc/data/cortex-settings-schema.json
[ -n "$ENV_SCHEMA_FILE" ] || ENV_SCHEMA_FILE=/var/lib/mscc/data/cortex-environment-schema.json

install -d -o "$AGENT_USER" -g "$AGENT_GROUP" -m 0750 "$STATE_DIR" "$STATE_DIR/backups"
grant_project_tree "$PROJECT_ROOT"

# Temporary Startup jobs run as a separate account. Give that account access
# only to ordinary project files plus read-only dependencies. Private/hidden
# files are also masked by the transient systemd sandbox in cortex-agent-control.
PROJECT_ROOT_REAL="$(readlink -f "$PROJECT_ROOT" 2>/dev/null || printf '%s' "$PROJECT_ROOT")"
find "$PROJECT_ROOT_REAL" \
  \( -type d \( -name node_modules -o -name .git -o -name .ssh -o -name .gradle -o -name .cortex \) -prune \) -o \
  \( -type f \( -name '.env*' -o -name '*.pem' -o -name '*.p12' -o -name '*.pfx' -o -name '*.key' -o -name '*.keystore' -o -name id_rsa -o -name id_ed25519 -o -name id_ecdsa -o -name id_dsa \) -prune \) -o \
  -exec setfacl -m "u:$RUNNER_USER:rwX" {} +
find "$PROJECT_ROOT_REAL" \
  \( -type d \( -name node_modules -o -name .git -o -name .ssh -o -name .gradle -o -name .cortex \) -prune \) -o \
  -type d -exec setfacl -m "d:u:$RUNNER_USER:rwX" {} +

# Revoke runner ACLs from protected files/directories on upgrades from an older
# Cortex Agent installer that may have granted a broader tree ACL.
find "$PROJECT_ROOT_REAL" -type f \
  \( -name '.env*' -o -name '*.pem' -o -name '*.p12' -o -name '*.pfx' -o -name '*.key' -o -name '*.keystore' -o -name id_rsa -o -name id_ed25519 -o -name id_ecdsa -o -name id_dsa \) \
  -exec setfacl -x "u:$RUNNER_USER" {} + 2>/dev/null || true
for protected_dir in .git .ssh .gradle .cortex; do
  while IFS= read -r -d '' directory; do
    setfacl -R -x "u:$RUNNER_USER" "$directory" 2>/dev/null || true
    find "$directory" -type d -exec setfacl -x "d:u:$RUNNER_USER" {} + 2>/dev/null || true
  done < <(find "$PROJECT_ROOT_REAL" -type d -name "$protected_dir" -print0)
done

if [ -d "$PROJECT_ROOT_REAL/node_modules" ]; then
  setfacl -Rm "u:$RUNNER_USER:rX" "$PROJECT_ROOT_REAL/node_modules"
  find "$PROJECT_ROOT_REAL/node_modules" -type d -exec setfacl -m "d:u:$RUNNER_USER:rX" {} +
fi

# Dependencies are deliberately hidden from the phone file API and excluded
# from backups, but the unprivileged Agent still needs to update an existing
# node_modules tree when the user runs Install dependencies.
grant_rw_tree "$PROJECT_ROOT/node_modules"
grant_read_tree "$SCHEMA_FILE"
grant_read_tree "$ENV_SCHEMA_FILE"

IFS=':' read -r -a private_paths <<<"$PRIVATE_PATHS"
for private_path in "${private_paths[@]}"; do
  [ -n "$private_path" ] || continue
  parent="$(dirname "$private_path")"
  [ "$parent" = "/" ] || setfacl -m "u:$AGENT_USER:--x" "$parent" 2>/dev/null || true
  grant_read_tree "$private_path"
done

# Settings writes require creating an atomic temp file beside the settings file.
# Apply this last so a broader private-backup read grant cannot downgrade it.
grant_rw_tree "$(dirname "$SETTINGS_FILE")"

systemctl daemon-reload
systemctl enable --now cortex-agent.service
systemctl --no-pager --full status cortex-agent.service || true

echo
echo "Cortex Agent is listening on 127.0.0.1:47831."
echo "Put it behind HTTPS before connecting the Android app."
