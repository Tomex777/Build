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
grep -Fq 'readonly INSTALL_BLOB=a058b3cf0e8fa09a117e816e5111ccb6bc8b58d0' "$UPGRADE"
grep -Fq 'readonly PACKAGE_BLOB=c2032a0fddf20a85e0cb1e9201bfd28652c046ba' "$UPGRADE"
grep -Fq 'readonly SERVICE_BLOB=82518da0a5a09d5148671aac1d8d3cfa3d7fb7fc' "$UPGRADE"
grep -Fq 'performing pinned first-time installation' "$UPGRADE"
grep -Fq 'sudo -n /usr/bin/bash "$setup_dir/install.sh"' "$UPGRADE"
grep -Fq '> /dev/null 2>&1' "$UPGRADE"
grep -Fq "trap 'rm -rf" "$UPGRADE"
if grep -Eq 'install-private\.log|cat.*CORTEX_AGENT_TOKEN|echo.*\$TOKEN' "$UPGRADE"; then
  echo 'Cortex installer must not persist or print the token.' >&2
  exit 1
fi
grep -Fq 'git hash-object "$tmp/index.js"' "$UPGRADE"
grep -Fq 'git hash-object "$tmp/cortex-agent-control"' "$UPGRADE"
grep -Fq 'MSCC_MEGA_SHARED_FOLDERS' "$UPGRADE"
grep -Fq 'MSCC_MEGA_PASSWORD' "$UPGRADE"
# Regression for 2026-10-15 phone Cloud screenshot: /status healthy while
# /environment fails. Marker must not bypass authenticated Cloud readback.
grep -Fq 'http://127.0.0.1:47831/api/cortex/host/environment' "$UPGRADE"
grep -Fq 'http://127.0.0.1:47831/api/cortex/host/cloud' "$UPGRADE"
grep -Fq "Cortex Cloud settings endpoint verified with protected credential masking." "$UPGRADE"
grep -Fq "Cortex Agent -> MSCC Community Cloud endpoint verified." "$UPGRADE"
# Agent's root helper writes a sibling env temp file under strict sandboxing.
# UNIX permissions + sudo helper still protect private system files.
grep -Fq 'readonly ENV_WRITER_OVERRIDE=' "$UPGRADE"
grep -Fq 'ReadWritePaths=/etc' "$UPGRADE"
grep -Fq 'systemctl daemon-reload' "$UPGRADE"
grep -Fq 'systemctl restart cortex-agent.service' "$UPGRADE"

grep -Fq "u:cortex-agent:r--" "$UPGRADE"
grep -Fq 'getfacl -cp "$directory"' "$UPGRADE"
grep -Fq 'if sudo -n test -f "$BOOTSTRAP_MARK"; then' "$UPGRADE"
env_line="$(grep -nF 'http://127.0.0.1:47831/api/cortex/host/environment' "$UPGRADE" | head -n 1 | cut -d: -f1)"
mark_line="$(grep -nF 'if sudo -n test -f "$BOOTSTRAP_MARK"; then' "$UPGRADE" | head -n 1 | cut -d: -f1)"
[[ "$env_line" -lt "$mark_line" ]] || { echo 'Bootstrap marker bypasses Cloud probe.' >&2; exit 1; }

# Regression: Cloud can pass on loopback while Android HTTPS fails.
grep -Fq 'readonly CORTEX_HTTPS_SITE=/etc/nginx/sites-enabled/cortex-agent' "$UPGRADE"
grep -Fq 'https_origin="https://${proxy_ip}"' "$UPGRADE"
grep -Fq 'https_resolution="${proxy_ip}:443:127.0.0.1"' "$UPGRADE"
grep -Fq 'Cortex nginx HTTPS ingress verified for authenticated Cloud and masked environment routes.' "$UPGRADE"
grep -Fq '"$https_origin/api/cortex/host/status"' "$UPGRADE"
grep -Fq '"$https_origin/api/cortex/host/$route"' "$UPGRADE"
grep -Fq '[[ "$anonymous_http" == 401 ]]' "$UPGRADE"
https_line="$(grep -nF 'Cortex nginx HTTPS ingress verified for authenticated Cloud and masked environment routes.' "$UPGRADE" | head -n 1 | cut -d: -f1)"
[[ "$https_line" -lt "$mark_line" ]] || { echo 'Bootstrap marker bypasses HTTPS verification.' >&2; exit 1; }

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
