#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE=/etc/nginx/sites-available/cortex-agent
LINK=/etc/nginx/sites-enabled/cortex-agent
[[ "$(readlink -f "$LINK")" == "$SITE" ]] || { echo 'Existing Cortex HTTPS site is required for NIGHT Play.' >&2; exit 1; }
backup="$(mktemp)"
probe="$(mktemp)"
probe_headers="$(mktemp)"
sudo cp -p "$SITE" "$backup"
# cp -p makes the backup root-owned on Azure. /tmp has the sticky bit:
# an unprivileged rm fails even if the deployment/validation succeeded.
# Keep the original root owner/mode for a lossless rollback; cleanup as root.
finish() {
  local result=$?
  trap - EXIT
  if [[ "$result" -ne 0 ]]; then
    if sudo cp -p "$backup" "$SITE" && sudo nginx -t && sudo systemctl reload nginx; then
      echo 'Restored the previous Cortex HTTPS configuration.' >&2
    else
      echo 'CRITICAL: Could not restore the previous Cortex HTTPS configuration.' >&2
    fi
  fi
  sudo rm -f -- "$backup" || echo 'WARNING: Failed to clean up protected nginx backup.' >&2
  rm -f -- "$probe" "$probe_headers" || true
  exit "$result"
}
trap finish EXIT

# First prove the updated MSCC daemon is serving the signed-only route
# directly. This distinguishes a missing backend handler from an nginx fault.
assert_unsigned_rejected() {
  local label="$1" url="$2" resolution="${3:-}" max_attempts="${4:-1}"
  local attempt status='000' route_seen='no' response_valid='no' connection_error='no'
  local -a resolution_args=()
  if [[ -n "$resolution" ]]; then resolution_args=(--resolve "$resolution"); fi
  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    if ! status="$(curl --silent --show-error --noproxy '*' --max-time 12 \
      "${resolution_args[@]}" --dump-header "$probe_headers" \
      --output "$probe" --write-out '%{http_code}' -X POST "$url")"; then
      status='000'
      connection_error='yes'
    fi
    # Check the MSCC response body, not a generic 401 returned by Cortex.
    response_valid='no'
    if [[ "$status" == 401 ]] && node - "$probe" <<'VERIFY'
const fs = require('node:fs');
let response;
try { response = JSON.parse(fs.readFileSync(process.argv[2], 'utf8')); }
catch { process.exit(1); }
if (response?.error !== 'Signed website request required.') process.exit(1);
VERIFY
    then
      response_valid='yes'
    fi
    route_seen='no'
    if [[ "$label" != 'HTTPS ingress' ]] ||
      grep -Eiq '^X-Night-Play-Route:[[:space:]]*signed-v1[[:space:]]*$' "$probe_headers"; then
      route_seen='yes'
    fi
    if [[ "$response_valid" == 'yes' && "$route_seen" == 'yes' ]]; then
      if ((attempt > 1)); then
        echo "NIGHT Play $label ready after nginx worker activation (attempt $attempt)."
      fi
      return 0
    fi
    if ((attempt < max_attempts)); then sleep 1; fi
  done
  # No user data, response body, headers, or credentials are logged.
  echo "NIGHT Play $label readiness failed after $max_attempts attempt(s): HTTP $status, expected MSCC challenge=$response_valid, route marker=$route_seen, transport failure=$connection_error." >&2
  return 1
}

assert_unsigned_rejected 'MSCC loopback' 'http://127.0.0.1:8787/api/night-play/rpc'

sudo /usr/bin/node "$SCRIPT_DIR/night-play-nginx.mjs" "$SITE"
sudo nginx -t
sudo systemctl reload nginx
ip="$(sudo sed -nE 's/^[[:space:]]*server_name[[:space:]]+([0-9]{1,3}(\.[0-9]{1,3}){3});[[:space:]]*$/\1/p' "$SITE" | head -n 1)"
[[ "$ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || {
  echo 'NIGHT Play nginx verification could not identify the existing Cortex HTTPS IPv4 host.' >&2
  exit 1
}
assert_unsigned_rejected 'HTTPS ingress' "https://$ip/api/night-play/rpc" "$ip:443:127.0.0.1" 15

# Protect the existing Cortex route; an unexpected response is a hard failure.
if ! status="$(curl --silent --show-error --noproxy '*' --max-time 12 \
  --resolve "$ip:443:127.0.0.1" --output /dev/null --write-out '%{http_code}' \
  "https://$ip/api/cortex/host/status")"; then
  echo 'Existing Cortex HTTPS authentication probe failed to connect.' >&2
  exit 1
fi
[[ "$status" == 401 ]] || {
  echo "Existing Cortex HTTPS authentication changed (HTTP $status)." >&2
  exit 1
}
echo 'NIGHT Play HTTPS ingress verified; unsigned requests rejected and Cortex authentication preserved.'
