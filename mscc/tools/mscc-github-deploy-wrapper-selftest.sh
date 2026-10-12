#!/usr/bin/env bash
set -euo pipefail
wrapper="$(dirname "$0")/mscc-github-deploy-wrapper.sh"
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
deploy_command="MSCC_SOURCE_REF=$sha MSCC_REUSE_EXISTING_SOURCE_STACK=1 bash -se"

as_service_user() {
  if [[ "$(id -u)" -eq 0 ]]; then
    command -v runuser >/dev/null || { echo "Cannot test non-root dispatcher as root without runuser." >&2; return 2; }
    runuser -u nobody -- "$@"
  else
    "$@"
  fi
}

out="$(printf '%s\n' 'printf wrapper-dispatch-ok' | as_service_user env SSH_ORIGINAL_COMMAND="$deploy_command" bash "$wrapper")"
[[ "$out" == "wrapper-dispatch-ok" ]] || { echo "Deploy command dispatch failed." >&2; exit 1; }
if as_service_user env SSH_ORIGINAL_COMMAND="bash -c id" bash "$wrapper" </dev/null >/dev/null 2>&1; then
  echo "Wrapper accepted an unapproved shell command." >&2
  exit 1
fi
grep -Fq 'exec /usr/bin/node /opt/mscc/current/tools/azure-main-diagnostics.mjs' "$wrapper"
echo "PASS forced-command wrapper accepts only pinned deploy and fixed read-only diagnostics commands"
