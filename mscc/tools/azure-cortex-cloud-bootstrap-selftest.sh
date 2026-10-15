#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPGRADE="$ROOT/tools/azure-cortex-cloud-bootstrap.sh"
INSTALL="$ROOT/install-azure.sh"

bash -n "$UPGRADE" "$INSTALL"
# Updater can only fetch an approved, fixed Cortex Agent revision and exact
# Git blob hashes. Never let environment variables or SSH input set its SHA.
grep -Fq 'readonly SOURCE_SHA=ad54464910918a2d62a4c560db565a2faaa4c20b' "$UPGRADE"
grep -Fq 'readonly AGENT_BLOB=b353643bf6978d71d76a066f84400aca100baecd' "$UPGRADE"
grep -Fq 'readonly HELPER_BLOB=b590dd2b6174c273ef3beef62f2825d6089d0311' "$UPGRADE"
grep -Fq 'git hash-object "$tmp/index.js"' "$UPGRADE"
grep -Fq 'git hash-object "$tmp/cortex-agent-control"' "$UPGRADE"
grep -Fq 'MSCC_MEGA_SHARED_FOLDERS' "$UPGRADE"
grep -Fq 'MSCC_MEGA_PASSWORD' "$UPGRADE"
grep -Fq 'Cortex Agent did not recover after upgrade.' "$UPGRADE"
grep -Fq 'sudo -n install -m 0644 "$tmp/previous-index.js"' "$UPGRADE"
grep -Fq 'sudo -n install -m 0755 "$tmp/previous-helper.sh"' "$UPGRADE"
grep -Fq 'sudo setfacl -m' "$INSTALL"
grep -Fq 'bash "$RELEASE/tools/azure-cortex-cloud-bootstrap.sh"' "$INSTALL"
if grep -Eq 'systemctl (restart|stop) mscc.service' "$UPGRADE"; then
  echo 'Cortex-only updater must not restart MSCC.' >&2
  exit 1
fi
if grep -Fq 'MSCC_SSH_KEY' "$UPGRADE" || grep -Fq 'SSH_ORIGINAL_COMMAND' "$UPGRADE"; then
  echo 'Do not bypass the restricted SSH deployment command.' >&2
  exit 1
fi
echo 'PASS: bootstrap is pinned, read-only until verified, and MSCC-safe.'
