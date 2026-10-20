#!/usr/bin/env bash
# One-time, pinned Cortex Cloud Agent bootstrap through the existing, authorized
# MSCC production deployment. Does not open arbitrary SSH commands or modify
# the restricted SSH forced-command policy.
set -euo pipefail
umask 077

readonly SOURCE_SHA=d280c2a67dd8ebce6927962cfb341122447ec777
readonly AGENT_BLOB=781d3dcd7b35a00e236abd8be70298170928f3c5
readonly PREVIOUS_AGENT_BLOB=558a8394d574aec9b8798e39d9d4475f6b6bf4a9
readonly HELPER_BLOB=8bc55110b92f60bfe31b8097cbc5dad32ae0a820
readonly PREVIOUS_HELPER_BLOB=4e0fa0455c604d3793a72254a1c3b4e260b67f4e
readonly INSTALL_BLOB=e5ac0fe87511310cc4248aa261a6cb185e3afe9d
readonly PACKAGE_BLOB=c2032a0fddf20a85e0cb1e9201bfd28652c046ba
readonly SERVICE_BLOB=82518da0a5a09d5148671aac1d8d3cfa3d7fb7fc
readonly AGENT_TARGET=/opt/cortex-agent/index.js
readonly HELPER_TARGET=/usr/local/libexec/cortex-agent-control
readonly SETTINGS_WRITE_BLOB=ad3f66e3b02ffca4367de009cda505e7f189db66
readonly SETTINGS_WRITE_TARGET=/usr/local/libexec/cortex-agent-settings-write.cjs
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
  for row in index.js cortex-agent-control install.sh package.json cortex-agent.service protected-settings-write.cjs; do
    curl --fail --silent --show-error --location --retry 3 --max-time 50 \
      "https://raw.githubusercontent.com/Tomex777/Build/$SOURCE_SHA/cortex-agent/$row" \
      --output "$setup_dir/$row"
  done
  for expected in \
    "$AGENT_BLOB index.js" "$HELPER_BLOB cortex-agent-control" \
    "$SETTINGS_WRITE_BLOB protected-settings-write.cjs" \
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
# Local loopback /status and Cloud checks are not sufficient: the Android
# app uses nginx HTTPS. Verify its authenticated HTTPS ingress independently
# so a green Azure deploy cannot mask a broken phone-facing route. No token,
# response body, public IP or credential is printed.
readonly CORTEX_HTTPS_SITE=/etc/nginx/sites-enabled/cortex-agent
if sudo -n test -f "$CORTEX_HTTPS_SITE"; then
  proxy_ip="$(sudo -n sed -nE 's/^[[:space:]]*server_name[[:space:]]+([0-9]{1,3}(\.[0-9]{1,3}){3});[[:space:]]*$/\1/p' "$CORTEX_HTTPS_SITE" | head -n 1)"
  if [[ ! "$proxy_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo 'Cortex HTTPS site exists but its certified IPv4 server_name is not identifiable.' >&2
    exit 1
  fi
  https_origin="https://${proxy_ip}"
  https_resolution="${proxy_ip}:443:127.0.0.1"
  anonymous_http="$(curl --silent --show-error --max-time 12 \
    --resolve "$https_resolution" --output /dev/null --write-out '%{http_code}' \
    "$https_origin/api/cortex/host/status")" || {
      echo 'Cortex HTTPS ingress cannot be reached with valid TLS.' >&2
      exit 1
    }
  [[ "$anonymous_http" == 401 ]] || {
    echo "Cortex HTTPS ingress failed the unauthenticated 401 check (HTTP $anonymous_http)." >&2
    exit 1
  }
  for route in environment cloud; do
    https_code="$(curl --silent --show-error --max-time 12 \
      --resolve "$https_resolution" \
      --output "$cloud_probe_dir/https-$route.json" --write-out '%{http_code}' \
      -H "Authorization: Bearer $CLOUD_TOKEN" \
      "$https_origin/api/cortex/host/$route")" || {
        echo "Cortex HTTPS $route endpoint is unreachable or its TLS certificate is invalid." >&2
        exit 1
      }
    [[ "$https_code" == 200 ]] || {
      echo "Cortex HTTPS $route endpoint returned HTTP $https_code." >&2
      exit 1
    }
  done
  node - "$cloud_probe_dir/https-environment.json" "$cloud_probe_dir/https-cloud.json" <<'VERIFY_HTTPS_CLOUD'
const fs = require('node:fs');
const env = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const entries = Array.isArray(env.entries) ? env.entries : [];
for (const key of ['MSCC_MEGA_EMAIL', 'MSCC_MEGA_PASSWORD', 'MSCC_MEGA_SHARED_FOLDERS']) {
  if (!entries.some(row => row.key === key)) throw new Error('Cortex HTTPS MEGA configuration is incomplete');
}
const password = entries.find(row => row.key === 'MSCC_MEGA_PASSWORD');
if (!password.secret || password.value) throw new Error('Cortex HTTPS response revealed protected credential data');
const cloud = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
if (typeof cloud.configured !== 'boolean') throw new Error('Cortex HTTPS Cloud response is invalid');
console.log('Cortex nginx HTTPS ingress verified for authenticated Cloud and masked environment routes.');
VERIFY_HTTPS_CLOUD
  # Production must prove the actual phone-facing read-only routes, not only
  # the ENV and Cloud pages. Mask all response bodies: they may contain logs,
  # account metadata or private file names. Never echo the bearer token.
  # A 200 HTML fallback is not valid: verify the expected JSON shape too.
  while IFS='|' read -r label endpoint shape; do
    [[ -n "$label" ]] || continue
    api_code="$(curl --silent --show-error --max-time 12 \
      --resolve "$https_resolution" \
      --output "$cloud_probe_dir/endpoint-check.json" --write-out '%{http_code}' \
      -H "Authorization: Bearer $CLOUD_TOKEN" \
      "$https_origin$endpoint")" || {
        echo "Cortex HTTPS $label endpoint is unreachable (transport/TLS error)." >&2
        exit 1
      }
    if [[ "$api_code" != 200 ]]; then
      echo "Cortex HTTPS $label endpoint failed: HTTP $api_code." >&2
      exit 1
    fi
    if ! node - "$cloud_probe_dir/endpoint-check.json" "$shape" <<'VERIFY_ENDPOINT_JSON'
const fs = require('node:fs');
const [file, shape] = process.argv.slice(2);
let data;
try { data = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { process.exit(2); }
if (!data || typeof data !== 'object' || Array.isArray(data)) process.exit(3);
if (shape === 'state' && typeof data.state !== 'string') process.exit(4);
if (shape !== 'state' && !Array.isArray(data[shape])) process.exit(5);
VERIFY_ENDPOINT_JSON
    then
      echo "Cortex HTTPS $label returned an incompatible response shape." >&2
      exit 1
    fi
  done <<'CORTEX_READ_ONLY_ENDPOINTS'
Status|/api/cortex/host/status|state
Console|/api/cortex/host/logs?limit=20|lines
Files|/api/cortex/host/files?path=%2F|entries
Startup|/api/cortex/host/startup|entries
Settings|/api/cortex/host/settings|entries
Activity|/api/cortex/host/activity?limit=10|entries
Backups|/api/cortex/host/backups|entries
Pairing|/api/cortex/mscc/pairing|accounts
Registry|/api/cortex/mscc/registry|modules
CORTEX_READ_ONLY_ENDPOINTS
  echo 'Cortex HTTPS read-only endpoint contract passed: status, logs, files, startup, settings, activity, backups, pairing, registry.'

else
  echo 'Cortex standard nginx HTTPS site not found: local Cloud works, phone-facing HTTPS is UNVERIFIED.'
fi

rm -rf "$cloud_probe_dir"
trap - EXIT

# Existing marker is historical evidence, NOT an upgrade gate. Only install
# the new pinned Agent if the existing binaries match the known predecessor;
# never overwrite a later/manual Agent revision or weaken forced-command SSH.
current_agent_blob="$(git hash-object "$AGENT_TARGET")"
current_helper_blob="$(git hash-object "$HELPER_TARGET")"
if [[ "$current_agent_blob" == "$AGENT_BLOB" && "$current_helper_blob" == "$HELPER_BLOB" && \
      "$(sudo -n test -f "$SETTINGS_WRITE_TARGET" && sudo -n git hash-object "$SETTINGS_WRITE_TARGET" || true)" == "$SETTINGS_WRITE_BLOB" ]]; then
  sudo -n grep -Fxq 'cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control setting-set *' /etc/sudoers.d/cortex-agent || {
    echo 'The protected Cortex settings write capability is not configured.' >&2; exit 1;
  }
  echo 'Approved Cortex Agent already active; retaining existing token and release.'
  sudo -n install -d -m 0750 /var/lib/cortex
  printf '%s\n' "$SOURCE_SHA" | sudo -n tee "$BOOTSTRAP_MARK" >/dev/null
  sudo -n chmod 0600 "$BOOTSTRAP_MARK"
  exit 0
fi
if [[ "$current_agent_blob" != "$PREVIOUS_AGENT_BLOB" ||
      "$current_helper_blob" != "$PREVIOUS_HELPER_BLOB" ]]; then
  echo 'Existing Cortex Agent differs from the approved predecessor; leaving it untouched. A dedicated audited upgrade is required.' >&2
  exit 0
fi
echo 'Upgrading verified previous Cortex Agent via the authorized protected MSCC release.'

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
    echo 'Cortex bootstrap failed; restoring only the former Agent, helper and privilege policy.' >&2
    sudo -n install -m 0644 "$tmp/previous-index.js" "$AGENT_TARGET" || true
    sudo -n install -m 0755 "$tmp/previous-helper.sh" "$HELPER_TARGET" || true
    if [[ -f "$tmp/previous-sudoers" ]]; then
      sudo -n install -m 0440 "$tmp/previous-sudoers" /etc/sudoers.d/cortex-agent || true
    fi
    if [[ -f "$tmp/previous-settings-helper" ]]; then
      sudo -n install -m 0644 "$tmp/previous-settings-helper" "$SETTINGS_WRITE_TARGET" || true
    else
      sudo -n rm -f "$SETTINGS_WRITE_TARGET" || true
    fi
    sudo -n systemctl restart cortex-agent.service || true
  fi
  rm -rf "$tmp"
  exit "$code"
}
trap rollback EXIT

for row in index.js cortex-agent-control protected-settings-write.cjs; do
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
[[ "$(git hash-object "$tmp/protected-settings-write.cjs")" == "$SETTINGS_WRITE_BLOB" ]] || {
  echo 'Cortex private settings helper does not match pinned Git blob.' >&2; exit 1;
}
node --check "$tmp/index.js"
bash -n "$tmp/cortex-agent-control"
node --check "$tmp/protected-settings-write.cjs"
cp "$AGENT_TARGET" "$tmp/previous-index.js"
sudo -n cat "$HELPER_TARGET" > "$tmp/previous-helper.sh"
sudo -n cat /etc/sudoers.d/cortex-agent > "$tmp/previous-sudoers"
if sudo -n test -e "$SETTINGS_WRITE_TARGET"; then
  # This is a new managed binary; never silently overwrite an unrelated file.
  [[ "$(sudo -n git hash-object "$SETTINGS_WRITE_TARGET")" == "$SETTINGS_WRITE_BLOB" ]] || {
    echo 'Existing protected Cortex writer does not match the approved pinned revision.' >&2; exit 1;
  }
  sudo -n cat "$SETTINGS_WRITE_TARGET" > "$tmp/previous-settings-helper"
fi

grep -Fq 'cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control env-set *' "$tmp/previous-sudoers" || {
  echo 'Existing Cortex sudoers contract is incompatible; refusing to broaden privileges.' >&2; exit 1;
}
cp "$tmp/previous-sudoers" "$tmp/new-sudoers"
if ! grep -Fxq 'cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control setting-set *' "$tmp/new-sudoers"; then
  printf '%s\n' 'cortex-agent ALL=(root) NOPASSWD: /usr/local/libexec/cortex-agent-control setting-set *' >> "$tmp/new-sudoers"
fi
sudo -n visudo -cf "$tmp/new-sudoers" >/dev/null

# All bytes and sudo policy are validated before modifying production.
changed=1
sudo -n install -m 0644 "$tmp/protected-settings-write.cjs" "$SETTINGS_WRITE_TARGET"
sudo -n install -m 0440 "$tmp/new-sudoers" /etc/sudoers.d/cortex-agent
sudo -n install -m 0644 "$tmp/index.js" "$AGENT_TARGET"
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
      const required=["MSCC_MEGA_EMAIL","MSCC_MEGA_PASSWORD","MSCC_MEGA_SHARED_FOLDERS","GROQ_API_KEY","AZURE_SPEECH_KEY","AZURE_OPENAI_API_KEY"];
      if (required.some(key=>!entries.some(row=>row.key===key))) process.exit(2);
      for (const key of ["MSCC_MEGA_PASSWORD","GROQ_API_KEY","AZURE_SPEECH_KEY","AZURE_OPENAI_API_KEY"]) {
        const secret=entries.find(row=>row.key===key);
        if (secret?.secret!==true || secret?.value) process.exit(3);
      }
      console.log("Cortex MEGA and AI environment controls verified; secrets remain hidden.");
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
