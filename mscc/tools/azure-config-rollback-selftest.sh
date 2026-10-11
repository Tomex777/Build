#!/usr/bin/env bash
set -euo pipefail

# Exercise the installer's actual config snapshot/restore functions in a
# disposable tree. Never read, change or log real /etc/mscc.env or systemd.
INSTALLER="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/install-azure.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT
awk '
  /^(snapshot_configs|restore_configs|cleanup_config_snapshot)\(\) \{/ { copying=1 }
  copying { print }
  copying && /^\}/ { copying=0 }
' "$INSTALLER" > "$TEST_ROOT/restore-functions.sh"
test -s "$TEST_ROOT/restore-functions.sh"
bash -n "$TEST_ROOT/restore-functions.sh"

mkdir -p "$TEST_ROOT/etc" "$TEST_ROOT/systemd" "$TEST_ROOT/backups"
BACKUPS="$TEST_ROOT/backups"
ENV_FILE="$TEST_ROOT/etc/mscc.env"
SERVICE="$TEST_ROOT/systemd/mscc.service"
CONFIG_ROLLBACK_DIR=""
ENV_PREEXISTED=0
SERVICE_PREEXISTED=0

# Real installer invokes sudo; in a temp directory it must be a no-op.
sudo() {
  if [[ "${1:-}" == 'systemctl' ]]; then
    [[ "${2:-}" == 'daemon-reload' ]] || return 1
    printf 'daemon-reload\n' >> "$TEST_ROOT/systemctl.txt"
  else
    "$@"
  fi
}
source "$TEST_ROOT/restore-functions.sh"

# Upgrade: restore exact original env secrets, permissions and unit file.
printf 'WEB_SESSION_SECRET=preserve-private-value\nMSCC_WEB_HOST=127.0.0.1\n' > "$ENV_FILE"
printf '[Service]\nExecStart=/usr/bin/node /opt/mscc/current/index.js\n' > "$SERVICE"
chmod 600 "$ENV_FILE"
chmod 644 "$SERVICE"
expected_env="$(sha256sum "$ENV_FILE" | cut -d' ' -f1)"
expected_service="$(sha256sum "$SERVICE" | cut -d' ' -f1)"
snapshot_configs
test -d "$CONFIG_ROLLBACK_DIR"
test "$(stat -c %a "$CONFIG_ROLLBACK_DIR")" = 700
test "$(stat -c %a "$CONFIG_ROLLBACK_DIR/mscc.env")" = 600
printf 'WEB_SESSION_SECRET=unwanted-change\n' > "$ENV_FILE"
printf '[Service]\nExecStart=/usr/bin/false\n' > "$SERVICE"
restore_configs
test "$(sha256sum "$ENV_FILE" | cut -d' ' -f1)" = "$expected_env"
test "$(sha256sum "$SERVICE" | cut -d' ' -f1)" = "$expected_service"
test "$(stat -c %a "$ENV_FILE")" = 600
test "$(stat -c %a "$SERVICE")" = 644
grep -Fxq 'daemon-reload' "$TEST_ROOT/systemctl.txt"
cleanup_config_snapshot
test -z "$CONFIG_ROLLBACK_DIR"
test -z "$(find "$BACKUPS" -mindepth 1 -print -quit)"

# First install: an aborted deployment must not leave newly created config.
rm "$ENV_FILE" "$SERVICE"
ENV_PREEXISTED=0
SERVICE_PREEXISTED=0
snapshot_configs
printf 'WEB_SESSION_SECRET=temporary\n' > "$ENV_FILE"
printf 'unit temporary\n' > "$SERVICE"
restore_configs
test ! -e "$ENV_FILE"
test ! -e "$SERVICE"
cleanup_config_snapshot
test -z "$(find "$BACKUPS" -mindepth 1 -print -quit)"
echo 'PASS MSCC private config and systemd rollback including first install and cleanup'
