#!/usr/bin/env bash
set -euo pipefail
umask 077

cmd="${SSH_ORIGINAL_COMMAND:-}"
if [[ "$cmd" == "mscc-main-diagnostics" ]]; then
  [[ "$(id -u)" -ne 0 ]] || { echo "Read-only monitor requires the existing non-root account." >&2; exit 2; }
  [[ -x /usr/bin/node && -f /opt/mscc/current/tools/azure-main-diagnostics.mjs ]] || {
    echo "Main diagnostics tool is unavailable." >&2
    exit 2
  }
  exec /usr/bin/node /opt/mscc/current/tools/azure-main-diagnostics.mjs
fi

if [[ "$cmd" =~ ^MSCC_SOURCE_REF=([0-9a-f]{40})\ MSCC_REUSE_EXISTING_SOURCE_STACK=1\ bash\ -se$ ]]; then
  [[ "$(id -u)" -ne 0 ]] || { echo "Use the existing non-root MSCC service account." >&2; exit 2; }
  export MSCC_SOURCE_REF="${BASH_REMATCH[1]}"
  export MSCC_REUSE_EXISTING_SOURCE_STACK=1
  exec /bin/bash -se
fi

echo "SSH key is restricted to the MSCC deploy and read-only Main monitor commands." >&2
exit 2
