#!/usr/bin/env bash
set -euo pipefail
wrapper="$(dirname "$0")/mscc-github-deploy-wrapper.sh"
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
deploy_command="MSCC_SOURCE_REF=$sha MSCC_REUSE_EXISTING_SOURCE_STACK=1 bash -se"
out="$(printf '%s\n' 'printf wrapper-dispatch-ok' | SSH_ORIGINAL_COMMAND="$deploy_command" bash "$wrapper")"
[[ "$out" == "wrapper-dispatch-ok" ]] || { echo "Deploy command dispatch failed." >&2; exit 1; }
if SSH_ORIGINAL_COMMAND="bash -c id" bash "$wrapper" </dev/null >/dev/null 2>&1; then
  echo "Wrapper accepted an unapproved shell command." >&2
  exit 1
fi
grep -Fq 'exec /usr/bin/node /opt/mscc/current/tools/azure-main-diagnostics.mjs' "$wrapper"
echo "PASS forced-command wrapper accepts only pinned deploy and fixed read-only diagnostics commands"
