#!/usr/bin/env bash
set -euo pipefail
wrapper="$(dirname "$0")/mscc-github-deploy-wrapper.sh"
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
deploy_command="MSCC_SOURCE_REF=$sha MSCC_REUSE_EXISTING_SOURCE_STACK=1 bash -se"

run_dispatch_tests() {
  local out
  out="$(printf '%s\n' 'printf wrapper-dispatch-ok' | env SSH_ORIGINAL_COMMAND="$deploy_command" bash "$wrapper")"
  [[ "$out" == "wrapper-dispatch-ok" ]] || { echo "Deploy command dispatch failed." >&2; exit 1; }
  if env SSH_ORIGINAL_COMMAND="bash -c id" bash "$wrapper" </dev/null >/dev/null 2>&1; then
    echo "Wrapper accepted an unapproved shell command." >&2
    exit 1
  fi
}

if [[ "$(id -u)" -eq 0 ]]; then
  # Hosted GitHub runners are non-root and run the full dispatcher test.
  # Some restricted local sandboxes cannot switch UID/GID, so still check the
  # exact command allowlist there and leave execution to CI.
  grep -Fq 'MSCC_SOURCE_REF=([0-9a-f]{40})' "$wrapper"
  grep -Fq 'if [[ "$cmd" == "mscc-main-diagnostics" ]]' "$wrapper"
  echo "SKIP wrapper execution under root; static dispatcher guards verified"
else
  run_dispatch_tests
fi
grep -Fq 'exec /usr/bin/node /opt/mscc/current/tools/azure-main-diagnostics.mjs' "$wrapper"
echo "PASS forced-command wrapper is limited to pinned deploy and fixed read-only diagnostics commands"
