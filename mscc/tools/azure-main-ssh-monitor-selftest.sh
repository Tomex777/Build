#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
script=mscc/tools/azure-main-ssh-monitor.sh
grep -Fq '"$USER_NAME@$HOST" mscc-main-diagnostics' "$script"
grep -Fq 'StrictHostKeyChecking=yes' "$script"
grep -Fq 'UserKnownHostsFile=$tmp/known_hosts' "$script"
grep -Fq 'for sample in 1 2 3 4 5 6' "$script"
grep -Fq 'sleep 30' "$script"
! grep -Eq 'pairingCode|pairingQr|numberMasked|disconnectReason|MSCC_SSH_KEY.*echo' "$script"
echo "PASS Main SSH monitor uses pinned host verification, fixed read-only command, sanitized output, and bounded polling"
