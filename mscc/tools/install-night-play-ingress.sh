#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE=/etc/nginx/sites-available/cortex-agent
LINK=/etc/nginx/sites-enabled/cortex-agent
[[ "$(readlink -f "$LINK")" == "$SITE" ]] || { echo 'Existing Cortex HTTPS site is required for NIGHT Play.' >&2; exit 1; }
backup="$(mktemp)"
probe="$(mktemp)"
sudo cp -p "$SITE" "$backup"
restore() {
  local status=$?
  if [[ "$status" -ne 0 ]]; then
    sudo cp -p "$backup" "$SITE"
    sudo nginx -t && sudo systemctl reload nginx || true
    echo 'Restored the previous Cortex HTTPS configuration.' >&2
  fi
  rm -f "$backup" "$probe"
}
trap restore EXIT
sudo /usr/bin/node "$SCRIPT_DIR/night-play-nginx.mjs" "$SITE"
sudo nginx -t
sudo systemctl reload nginx
ip="$(sudo sed -nE 's/^[[:space:]]*server_name[[:space:]]+([0-9]{1,3}(\.[0-9]{1,3}){3});[[:space:]]*$/\1/p' "$SITE")"
[[ "$ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || exit 1
status="$(curl --silent --show-error --max-time 12 --resolve "$ip:443:127.0.0.1" \
  --output "$probe" --write-out '%{http_code}' -X POST "https://$ip/api/night-play/rpc")"
[[ "$status" == 401 ]] || { echo 'NIGHT Play ingress must reject unsigned requests.' >&2; exit 1; }
node - "$probe" <<'VERIFY'
const fs=require('node:fs');
if(JSON.parse(fs.readFileSync(process.argv[2],'utf8')).error!=='Signed website request required.')process.exit(1);
VERIFY
status="$(curl --silent --show-error --max-time 12 --resolve "$ip:443:127.0.0.1" \
  --output /dev/null --write-out '%{http_code}' "https://$ip/api/cortex/host/status")"
[[ "$status" == 401 ]] || { echo 'Existing Cortex ingress authentication check failed.' >&2; exit 1; }
echo 'NIGHT Play HTTPS ingress verified; unsigned requests rejected and Cortex authentication preserved.'
