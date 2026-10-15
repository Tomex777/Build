#!/usr/bin/env bash
# One-time, pinned Cortex Cloud Agent bootstrap through the existing, authorized
# MSCC production deployment. Does not open arbitrary SSH commands or modify
# the restricted SSH forced-command policy.
set -euo pipefail
umask 077

readonly SOURCE_SHA=ad54464910918a2d62a4c560db565a2faaa4c20b
readonly AGENT_BLOB=b353643bf6978d71d76a066f84400aca100baecd
readonly HELPER_BLOB=b590dd2b6174c273ef3beef62f2825d6089d0311
readonly INSTALL_BLOB=a058b3cf0e8fa09a117e816e5111ccb6bc8b58d0
readonly PACKAGE_BLOB=c2032a0fddf20a85e0cb1e9201bfd28652c046ba
readonly SERVICE_BLOB=82518da0a5a09d5148671aac1d8d3cfa3d7fb7fc
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
systemctl is-active --quiet mscc.service || { echo 'MSCC is not active; declining Agent bootstrap.' >&2; exit 2; }

# Production investigation (15 Oct): the server is missing either the Agent
# binary or privileged helper. Older rollouts assumed an existing install.
# Never open the deliberately restricted GitHub SSH forced command to fix it.
# Install the *pinned official Cortex package* through the authorized MSCC
# deployment if the Agent is absent, incomplete, or inactive. Existing
# /etc/cortex-agent.env (and its token) is preserved by the installer.
if [[ ! -f "$AGENT_TARGET" || ! -f "$HELPER_TARGET" ]] ||
    ! systemctl is-active --quiet cortex-agent.service; then
  echo 'Existing Cortex Agent incomplete or inactive; performing pinned first-time installation.'
  setup_dir="$(mktemp -d)"
  trap 'rm -rf "$setup_dir"' EXIT
  for row in index.js cortex-agent-control install.sh package.json cortex-agent.service; do
    curl --fail --silent --show-error --location --retry 3 --max-time 50 \
      "https://raw.githubusercontent.com/Tomex777/Build/$SOURCE_SHA/cortex-agent/$row" \
      --output "$setup_dir/$row"
  done
  for expected in \
    "$AGENT_BLOB index.js" "$HELPER_BLOB cortex-agent-control" \
    "$INSTALL_BLOB install.sh" "$PACKAGE_BLOB package.json" \
    "$SERVICE_BLOB cortex-agent.service"; do
    read -r blob filename <<< "$expected"
    [[ "$(git hash-object "$setup_dir/$filename")" == "$blob" ]] || {
      echo 'Pinned Cortex installer integrity check failed.' >&2; exit 1;
    }
  done
  node --check "$setup_dir/index.js"
  bash -n "$setup_dir/install.sh"
  bash -n "$setup_dir/cortex-agent-control"
  # The upstream installer can print the freshly generated Agent token.
  # Absolutely never stream its stdout/stderr to public CI logs.
  if ! sudo -n /usr/bin/bash "$setup_dir/install.sh" \
      > /dev/null 2>&1; then
    echo 'Pinned Cortex first-time installer failed; inspect private host log only.' >&2
    exit 1
  fi
  rm -rf "$setup_dir"
  trap - EXIT
  echo 'Pinned Cortex service installed; existing configuration retained.'
fi
systemctl is-active --quiet cortex-agent.service || {
  echo 'Cortex Agent is not healthy after bootstrap.' >&2; exit 1;
}
[[ -f "$AGENT_TARGET" && -f "$HELPER_TARGET" ]] || {
  echo 'Cortex Agent files still missing after first-time bootstrap.' >&2; exit 1;
}
# The Agent runs unprivileged with ProtectSystem=strict. Its narrowly
# allowlisted root helper atomically writes /etc/mscc.env by creating a sibling
# temp file in /etc. A writable-file exception alone cannot rename it; the
# protected *directory* must be writable inside the service mount namespace.
# UNIX root ownership and the strictly validated sudo helper remain in force:
# unprivileged Agent code still cannot modify other /etc files.
readonly ENV_WRITER_OVERRIDE=/etc/systemd/system/cortex-agent.service.d/managed-env-writes.conf
if ! sudo -n grep -Fxq 'ReadWritePaths=/etc' "$ENV_WRITER_OVERRIDE" 2>/dev/null; then
  sudo -n install -d -m 0755 /etc/systemd/system/cortex-agent.service.d
  printf '[Service]\nReadWritePaths=/etc\n' \
    | sudo -n tee "$ENV_WRITER_OVERRIDE" >/dev/null
  sudo -n chmod 0644 "$ENV_WRITER_OVERRIDE"
  sudo -n systemctl daemon-reload
  sudo -n systemctl restart cortex-agent.service
  agent_started=0
  for attempt in {1..12}; do
    if systemctl is-active --quiet cortex-agent.service; then
      agent_started=1
      break
    fi
    sleep 1
  done
  [[ "$agent_started" == 1 ]] || {
    echo 'Cortex Agent did not recover after enabling protected environment updates.' >&2
    exit 1
  }
fi

# MSCC chmod(700) resets POSIX ACL masks on /var/lib/mscc/data.
# The installed Agent may be healthy for /status while failing /environment.
# Repair only the exact read/traversal privileges needed by the Cloud screen.
# Never grant the temporary job runner access or modify WhatsApp auth.
if ! command -v setfacl >/dev/null; then
  echo 'Cortex Cloud readiness requires POSIX ACL support.' >&2
  exit 1
fi
if ! id -u cortex-agent >/dev/null 2>&1; then
  echo 'Cortex service account unavailable after Agent bootstrap.' >&2
  exit 1
fi
sudo -n setfacl -m 'u:cortex-agent:r--' /etc/mscc.env
for directory in /var/lib/mscc /var/lib/mscc/data; do
  if sudo -n test -d "$directory"; then
    # Preserve the existing named-user ACL (including any intentional write
    # access used for Cortex settings) while recomputing the mask after chmod.
    current_acl="$(sudo -n getfacl -cp "$directory" 2>/dev/null \
      | sed -n 's/^user:cortex-agent:\([rwx-][rwx-][rwx-]\).*$/\1/p' | head -n 1)"
    if [[ -z "$current_acl" ]]; then current_acl='--x'; fi
    if [[ "$current_acl" != *x* ]]; then
      current_acl="${current_acl:0:2}x"
    fi
    sudo -n setfacl -m "u:cortex-agent:$current_acl" "$directory"
  fi
done
for schema in /var/lib/mscc/data/cortex-environment-schema.json /var/lib/mscc/data/cortex-settings-schema.json; do
  if sudo -n test -f "$schema"; then
    sudo -n setfacl -m 'u:cortex-agent:r--' "$schema"
  fi
done
if ! sudo -n -u cortex-agent test -r /etc/mscc.env; then
  echo 'Cortex process cannot read its protected MSCC environment.' >&2
  exit 1
fi

# Validate the actual route used by the Android Cloud tab even on upgrades
# already marked as bootstrapped. A healthy /status alone is insufficient.
CLOUD_TOKEN="$(sudo -n sed -n 's/^CORTEX_AGENT_TOKEN=//p' /etc/cortex-agent.env | tail -n 1)"
[[ -n "$CLOUD_TOKEN" ]] || { echo 'Agent token is missing on the VM.' >&2; exit 1; }
cloud_probe_dir="$(mktemp -d)"
probe_cleanup() { rm -rf "$cloud_probe_dir"; }
trap probe_cleanup EXIT
http_code="$(curl --silent --show-error --max-time 12 \
  --output "$cloud_probe_dir/environment.json" --write-out '%{http_code}' \
  -H "Authorization: Bearer $CLOUD_TOKEN" \
  http://127.0.0.1:47831/api/cortex/host/environment)" || {
    echo 'Local Cortex environment route is unreachable.' >&2
    exit 1
  }
if [[ "$http_code" != 200 ]]; then
  echo "Cortex Cloud environment route returned HTTP $http_code after permission repair." >&2
  exit 1
fi
node - "$cloud_probe_dir/environment.json" <<'VERIFY_CLOUD_SCHEMA'
const fs = require('node:fs');
const schema = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const entries = Array.isArray(schema?.entries) ? schema.entries : [];
for (const key of ['MSCC_MEGA_EMAIL', 'MSCC_MEGA_PASSWORD', 'MSCC_MEGA_SHARED_FOLDERS']) {
  if (!entries.some(e => e.key === key)) {
    throw new Error('Expected managed MEGA key absent: ' + key);
  }
}
const password = entries.find(e => e.key === 'MSCC_MEGA_PASSWORD');
if (!password.secret || password.value) throw new Error('Cortex MEGA password masking invariant failed');
console.log('Cortex Cloud settings endpoint verified with protected credential masking.');
VERIFY_CLOUD_SCHEMA
cloud_http="$(curl --silent --show-error --max-time 12 \
  --output "$cloud_probe_dir/cloud.json" --write-out '%{http_code}' \
  -H "Authorization: Bearer $CLOUD_TOKEN" \
  http://127.0.0.1:47831/api/cortex/host/cloud)" || {
    echo 'Cortex Cloud route is unreachable.' >&2
    exit 1
  }
if [[ "$cloud_http" != 200 ]]; then
  echo "Cortex Cloud proxy route returned HTTP $cloud_http." >&2
  exit 1
fi
node - "$cloud_probe_dir/cloud.json" <<'VERIFY_CLOUD_RESPONSE'
const fs = require('node:fs');
const cloud = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
if (!cloud || typeof cloud !== 'object' || typeof cloud.configured !== 'boolean') {
  throw new Error('Unexpected MSCC Cloud dashboard response.');
}
console.log('Cortex Agent -> MSCC Community Cloud endpoint verified.');
VERIFY_CLOUD_RESPONSE
rm -rf "$cloud_probe_dir"
trap - EXIT

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
