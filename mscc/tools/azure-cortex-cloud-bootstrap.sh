#!/usr/bin/env bash
# One-time, pinned Cortex Cloud Agent bootstrap through the existing, authorized
# MSCC production deployment. Does not open arbitrary SSH commands or modify
# the restricted SSH forced-command policy.
set -euo pipefail
umask 077

readonly SOURCE_SHA=ad54464910918a2d62a4c560db565a2faaa4c20b
readonly AGENT_BLOB=b353643bf6978d71d76a066f84400aca100baecd
readonly HELPER_BLOB=b590dd2b6174c273ef3beef62f2825d6089d0311
readonly AGENT_TARGET=/opt/cortex-agent/index.js
readonly HELPER_TARGET=/usr/local/libexec/cortex-agent-control
readonly BOOTSTRAP_MARK=/var/lib/cortex/.mscc-mega-cloud-bootstrapped

if [[ "$(id -u)" -eq 0 ]]; then
  echo 'Run from the approved non-root MSCC deploy account.' >&2
  exit 2
fi
for required in sudo curl node git systemctl; do
  command -v "$required" >/dev/null || { echo "Required deployment executable unavailable: $required" >&2; exit 2; }
done
sudo -n true || { echo 'Noninteractive Azure sudo is required.' >&2; exit 2; }
# Cortex is a separate optional service. Its absence must not revert a healthy
# MSCC release. An *incomplete* existing Cortex installation still fails closed.
if [[ ! -e "$AGENT_TARGET" && ! -e "$HELPER_TARGET" ]]; then
  echo 'Optional Cortex Agent not installed; deferring Cloud Agent upgrade without blocking MSCC deployment.'
  exit 0
fi
systemctl is-active --quiet cortex-agent.service || { echo 'Existing Cortex Agent service is not active.' >&2; exit 2; }
systemctl is-active --quiet mscc.service || { echo 'MSCC is not active; declining Agent bootstrap.' >&2; exit 2; }
[[ -f "$AGENT_TARGET" ]] && [[ -f "$HELPER_TARGET" ]] || {
  echo 'Cortex Agent has not been installed; run its installer separately.' >&2
  exit 2
}
# Never force an old pinned Agent over a subsequent, newer update.
if sudo -n test -f "$BOOTSTRAP_MARK"; then
  echo 'Cortex Cloud bootstrap already completed; leaving later Agent updates untouched.'
  exit 0
fi
if [[ "$(git hash-object "$AGENT_TARGET")" == "$AGENT_BLOB" ]]; then
  echo 'Cortex Cloud Agent already installed. Recording bootstrap state.'
  sudo -n install -d -m 0750 /var/lib/cortex
  printf '%s\n' "$SOURCE_SHA" | sudo -n tee "$BOOTSTRAP_MARK" >/dev/null
  sudo -n chmod 0600 "$BOOTSTRAP_MARK"
  exit 0
fi

# Do not leak the credential or any account data into CI logs.
TOKEN="$(sudo -n sed -n 's/^CORTEX_AGENT_TOKEN=//p' /etc/cortex-agent.env | tail -n 1)"
[[ -n "$TOKEN" ]] || { echo 'Existing Cortex Agent credential not found.' >&2; exit 1; }
curl -fsS --max-time 12 -o /dev/null \
  -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:47831/api/cortex/host/status

readonly MSCC_PID="$(systemctl show mscc.service --property MainPID --value)"
[[ "$MSCC_PID" =~ ^[0-9]+$ ]] && ((MSCC_PID > 1)) || {
  echo 'Refusing Cortex-only rollout while MSCC process is unhealthy.' >&2; exit 1;
}

tmp="$(mktemp -d)"
changed=0
rollback() {
  code=$?
  trap - EXIT
  if ((code != 0 && changed == 1)); then
    echo 'Cortex bootstrap failed; restoring only the former Agent and helper.' >&2
    sudo -n install -m 0644 "$tmp/previous-index.js" "$AGENT_TARGET" || true
    sudo -n install -m 0755 "$tmp/previous-helper.sh" "$HELPER_TARGET" || true
    sudo -n systemctl restart cortex-agent.service || true
  fi
  rm -rf "$tmp"
  exit "$code"
}
trap rollback EXIT

for row in index.js cortex-agent-control; do
  curl --fail --silent --show-error --location --retry 3 --max-time 40 \
    "https://raw.githubusercontent.com/Tomex777/Build/$SOURCE_SHA/cortex-agent/$row" \
    --output "$tmp/$row"
done
[[ "$(git hash-object "$tmp/index.js")" == "$AGENT_BLOB" ]] || {
  echo 'Cortex Cloud Agent does not match pinned Git blob.' >&2; exit 1;
}
[[ "$(git hash-object "$tmp/cortex-agent-control")" == "$HELPER_BLOB" ]] || {
  echo 'Cortex Cloud helper does not match pinned Git blob.' >&2; exit 1;
}
node --check "$tmp/index.js"
bash -n "$tmp/cortex-agent-control"
cp "$AGENT_TARGET" "$tmp/previous-index.js"
sudo -n cat "$HELPER_TARGET" > "$tmp/previous-helper.sh"

sudo -n install -m 0644 "$tmp/index.js" "$AGENT_TARGET"
changed=1
sudo -n install -m 0755 "$tmp/cortex-agent-control" "$HELPER_TARGET"
sudo -n systemctl restart cortex-agent.service

healthy=0
for attempt in {1..15}; do
  if curl -fsS --max-time 4 -o /dev/null \
      -H "Authorization: Bearer $TOKEN" \
      http://127.0.0.1:47831/api/cortex/host/status; then
    healthy=1
    break
  fi
  sleep 1
done
[[ "$healthy" == 1 ]] || { echo 'Cortex Agent did not recover after upgrade.' >&2; exit 1; }

curl -fsS --max-time 12 \
  -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:47831/api/cortex/host/environment \
  | node -e '
    let body="";
    process.stdin.on("data", chunk=>body+=chunk);
    process.stdin.on("end", () => {
      const response=JSON.parse(body);
      const entries=Array.isArray(response.entries)?response.entries:[];
      const required=["MSCC_MEGA_EMAIL","MSCC_MEGA_PASSWORD","MSCC_MEGA_SHARED_FOLDERS"];
      if (required.some(key=>!entries.some(row=>row.key===key))) process.exit(2);
      const password=entries.find(row=>row.key==="MSCC_MEGA_PASSWORD");
      if (password?.secret!==true || password?.value) process.exit(3);
      console.log("Cortex MEGA Cloud configuration route verified; secrets remain hidden.");
    });'
# Verify route exists; downstream /cloud may remain unavailable until the
# separately authorized MSCC Cloud release is installed.
status="$(curl -sS --max-time 12 \
  -H "Authorization: Bearer $TOKEN" \
  --output "$tmp/cloud-check.json" --write-out '%{http_code}' \
  http://127.0.0.1:47831/api/cortex/host/cloud)"
case "$status" in
  200) echo 'Cortex Cloud proxy is available.' ;;
  502) echo 'Cortex Cloud route available; downstream MSCC Cloud not ready.' ;;
  *) echo "Cortex Cloud proxy returned unexpected HTTP $status" >&2; exit 1 ;;
esac
systemctl is-active --quiet mscc.service
[[ "$(systemctl show mscc.service --property MainPID --value)" == "$MSCC_PID" ]] || {
  echo 'MSCC process unexpectedly changed during Cortex-only bootstrap.' >&2
  exit 1
}

sudo -n install -d -m 0750 /var/lib/cortex
printf '%s\n' "$SOURCE_SHA" | sudo -n tee "$BOOTSTRAP_MARK" >/dev/null
sudo -n chmod 0600 "$BOOTSTRAP_MARK"
changed=0
echo 'Cortex MEGA Cloud Agent successfully bootstrapped without MSCC restart.'
