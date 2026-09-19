#!/usr/bin/env bash
set -Eeuo pipefail

# Homira TURN: all-in-one Azure provisioner
#
# What it does:
#   1) Uses the currently authenticated Azure CLI subscription.
#   2) Probes the known-good candidate regions for Standard_B1s availability
#      under THIS subscription.
#   3) Creates the resource group, static Standard IPv4, NSG rules, VNet,
#      NIC, and Ubuntu VM.
#   4) Uses Azure Run Command (no SSH login required) to install Docker
#      and run Coturn 4.18.0.
#   5) Generates a random 256-bit Coturn REST shared secret.
#   6) If SUPABASE_ACCESS_TOKEN is set, pushes the TURN secret + URLs
#      directly to the Homira Supabase project's Edge Function secrets.
#      The secret is NEVER printed.
#
# Recommended: run this from Azure Cloud Shell (Bash).
#
# Fully automatic usage:
#   export SUPABASE_ACCESS_TOKEN='YOUR_SUPABASE_PERSONAL_ACCESS_TOKEN'
#   bash homira-turn-azure-all-in-one.sh
#   unset SUPABASE_ACCESS_TOKEN
#
# Optional overrides:
#   REGION=switzerlandnorth bash homira-turn-azure-all-in-one.sh
#   SIZE=Standard_B1s bash homira-turn-azure-all-in-one.sh
#   ROTATE_TURN_SECRET=1 bash homira-turn-azure-all-in-one.sh
#
# The script deliberately does NOT fall back to a larger VM size:
# Standard_B1s is the intended Homira TURN VM.

RG="${RG:-homira-turn-rg}"
VM="${VM:-homira-turn-01}"
SIZE="${SIZE:-Standard_B1s}"
ADMIN_USER="${ADMIN_USER:-azureuser}"
IMAGE="${IMAGE:-Canonical:0001-com-ubuntu-server-jammy:22_04-lts-gen2:latest}"

VNET="${VNET:-homira-turn-vnet}"
SUBNET="${SUBNET:-homira-turn-subnet}"
NSG="${NSG:-homira-turn-nsg}"
NIC="${NIC:-homira-turn-nic}"
PIP="${PIP:-homira-turn-ip}"

TURN_REALM="${TURN_REALM:-homira}"
TURN_LISTEN_PORT="${TURN_LISTEN_PORT:-3478}"
TURN_RELAY_MIN_PORT="${TURN_RELAY_MIN_PORT:-49160}"
TURN_RELAY_MAX_PORT="${TURN_RELAY_MAX_PORT:-49200}"
TURN_TTL_SECONDS="${TURN_TTL_SECONDS:-86400}"
TURN_IMAGE="${TURN_IMAGE:-coturn/coturn:4.18.0}"

SUPABASE_PROJECT_REF="${SUPABASE_PROJECT_REF:-uhyeopkbamwtgjgeqlyj}"
SUPABASE_CLI_VERSION="${SUPABASE_CLI_VERSION:-2.117.0}"

ROTATE_TURN_SECRET="${ROTATE_TURN_SECRET:-0}"
REGION="${REGION:-}"

CANDIDATE_REGIONS=(
  southafricanorth
  uaenorth
  switzerlandnorth
  spaincentral
  austriaeast
)

log() {
  printf '\n\033[1;36m[Homira TURN]\033[0m %s\n' "$*"
}

warn() {
  printf '\n\033[1;33m[Homira TURN]\033[0m %s\n' "$*" >&2
}

die() {
  printf '\n\033[1;31m[Homira TURN ERROR]\033[0m %s\n' "$*" >&2
  exit 1
}

cleanup() {
  unset TURN_SHARED_SECRET 2>/dev/null || true
}
trap cleanup EXIT

command -v az >/dev/null 2>&1 || die "Azure CLI (az) is required. Run this from Azure Cloud Shell."
command -v openssl >/dev/null 2>&1 || die "openssl is required."
command -v npx >/dev/null 2>&1 || warn "npx is not available. Azure provisioning will work, but automatic Supabase secret sync cannot run."

az account show >/dev/null 2>&1 || die "Azure CLI is not authenticated. Azure Cloud Shell normally signs you in automatically."

SUBSCRIPTION_NAME="$(az account show --query name -o tsv)"
SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
log "Using Azure subscription: ${SUBSCRIPTION_NAME} (${SUBSCRIPTION_ID})"

VM_EXISTS=0
if az vm show -g "$RG" -n "$VM" >/dev/null 2>&1; then
  VM_EXISTS=1
  EXISTING_REGION="$(az vm show -g "$RG" -n "$VM" --query location -o tsv)"
  EXISTING_SIZE="$(az vm show -g "$RG" -n "$VM" --query hardwareProfile.vmSize -o tsv)"
  REGION="$EXISTING_REGION"
  log "Existing VM found: ${VM} (${EXISTING_SIZE}) in ${REGION}; infrastructure creation will be skipped."
else
  if [[ -n "$REGION" ]]; then
    REGIONS_TO_CHECK=("$REGION")
  else
    REGIONS_TO_CHECK=("${CANDIDATE_REGIONS[@]}")
  fi

  log "Checking ${SIZE} availability for this subscription..."
  CHOSEN_REGION=""

  for r in "${REGIONS_TO_CHECK[@]}"; do
    printf '  checking %-20s ... ' "$r"
    AVAILABLE="$(
      az vm list-skus         --location "$r"         --size "$SIZE"         --resource-type virtualMachines         --all         --query "[?name=='${SIZE}' && length(restrictions)==\`0\`].name | [0]"         -o tsv 2>/dev/null || true
    )"

    if [[ "$AVAILABLE" == "$SIZE" ]]; then
      echo "available"
      CHOSEN_REGION="$r"
      break
    else
      echo "not available/restricted"
    fi
  done

  [[ -n "$CHOSEN_REGION" ]] || die "${SIZE} is restricted/unavailable in the candidate regions for this subscription. No larger paid VM was created."
  REGION="$CHOSEN_REGION"
  log "Selected region: ${REGION}"

  log "Creating resource group..."
  az group create --name "$RG" --location "$REGION" --output none

  log "Creating Network Security Group..."
  if ! az network nsg show -g "$RG" -n "$NSG" >/dev/null 2>&1; then
    az network nsg create --resource-group "$RG" --name "$NSG" --location "$REGION" --output none
  fi

  ensure_nsg_rule() {
    local name="$1"
    local priority="$2"
    local protocol="$3"
    local ports="$4"

    if az network nsg rule show -g "$RG" --nsg-name "$NSG" -n "$name" >/dev/null 2>&1; then
      az network nsg rule update         --resource-group "$RG" --nsg-name "$NSG" --name "$name"         --priority "$priority" --direction Inbound --access Allow         --protocol "$protocol" --source-address-prefixes '*' --source-port-ranges '*'         --destination-address-prefixes '*' --destination-port-ranges "$ports" --output none
    else
      az network nsg rule create         --resource-group "$RG" --nsg-name "$NSG" --name "$name"         --priority "$priority" --direction Inbound --access Allow         --protocol "$protocol" --source-address-prefixes '*' --source-port-ranges '*'         --destination-address-prefixes '*' --destination-port-ranges "$ports" --output none
    fi
  }

  ensure_nsg_rule "Allow-SSH"       100 Tcp 22
  ensure_nsg_rule "Allow-TURN-UDP"  110 Udp "$TURN_LISTEN_PORT"
  ensure_nsg_rule "Allow-TURN-TCP"  120 Tcp "$TURN_LISTEN_PORT"
  ensure_nsg_rule "Allow-Relay-UDP" 130 Udp "${TURN_RELAY_MIN_PORT}-${TURN_RELAY_MAX_PORT}"

  log "Creating VNet/subnet..."
  if ! az network vnet show -g "$RG" -n "$VNET" >/dev/null 2>&1; then
    az network vnet create       --resource-group "$RG" --name "$VNET" --location "$REGION"       --address-prefixes 10.42.0.0/16 --subnet-name "$SUBNET"       --subnet-prefixes 10.42.0.0/24 --output none
  fi

  log "Creating static Standard public IPv4..."
  if ! az network public-ip show -g "$RG" -n "$PIP" >/dev/null 2>&1; then
    az network public-ip create       --resource-group "$RG" --name "$PIP" --location "$REGION"       --version IPv4 --sku Standard --allocation-method Static --tier Regional --output none
  fi

  log "Creating NIC..."
  if ! az network nic show -g "$RG" -n "$NIC" >/dev/null 2>&1; then
    az network nic create       --resource-group "$RG" --name "$NIC" --location "$REGION"       --vnet-name "$VNET" --subnet "$SUBNET"       --network-security-group "$NSG" --public-ip-address "$PIP" --output none
  fi

  log "Creating ${SIZE} VM..."
  az vm create     --resource-group "$RG" --name "$VM" --location "$REGION"     --image "$IMAGE" --size "$SIZE" --admin-username "$ADMIN_USER"     --generate-ssh-keys --nics "$NIC" --storage-sku StandardSSD_LRS     --os-disk-size-gb 30 --os-disk-delete-option Delete     --security-type Standard --output none

  log "VM created."
fi

PUBLIC_IP="$(az network public-ip show --resource-group "$RG" --name "$PIP" --query ipAddress -o tsv)"
[[ "$PUBLIC_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || die "Could not determine the VM's public IPv4 address."

NEED_NEW_SECRET=1

if [[ "$VM_EXISTS" == "1" && "$ROTATE_TURN_SECRET" != "1" ]]; then
  log "Checking whether Coturn is already configured..."
  REMOTE_STATUS="$(
    az vm run-command invoke       --resource-group "$RG" --name "$VM" --command-id RunShellScript       --scripts 'if [ -s /opt/homira-turn/turnserver.conf ] && docker ps --format "{{.Names}}" 2>/dev/null | grep -qx homira-coturn; then echo HOMIRA_TURN_READY; else echo HOMIRA_TURN_NEEDS_SETUP; fi'       --query "value[0].message" -o tsv 2>/dev/null || true
  )"

  if grep -q "HOMIRA_TURN_READY" <<<"$REMOTE_STATUS"; then
    NEED_NEW_SECRET=0
    log "Existing Coturn installation is already running. Secret rotation skipped."
  fi
fi

if [[ "$ROTATE_TURN_SECRET" == "1" ]]; then
  log "TURN secret rotation explicitly requested."
  NEED_NEW_SECRET=1
fi

if [[ "$NEED_NEW_SECRET" == "1" ]]; then
  TURN_SHARED_SECRET="$(openssl rand -hex 32)"
  [[ ${#TURN_SHARED_SECRET} -eq 64 ]] || die "Failed to generate the TURN shared secret."

  BOOTSTRAP_FILE="$(mktemp)"
  chmod 600 "$BOOTSTRAP_FILE"

  cat > "$BOOTSTRAP_FILE" <<'REMOTE_SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail

TURN_SHARED_SECRET="${1:?missing TURN secret}"
TURN_EXTERNAL_IP="${2:?missing public IP}"
TURN_REALM="${3:-homira}"
TURN_LISTEN_PORT="${4:-3478}"
TURN_RELAY_MIN_PORT="${5:-49160}"
TURN_RELAY_MAX_PORT="${6:-49200}"
TURN_IMAGE="${7:-coturn/coturn:4.18.0}"

export DEBIAN_FRONTEND=noninteractive

apt-get update -y
apt-get install -y ca-certificates docker.io iproute2
systemctl enable --now docker

install -d -m 700 /opt/homira-turn

umask 077
cat > /opt/homira-turn/turnserver.conf <<EOF
fingerprint
use-auth-secret
static-auth-secret=${TURN_SHARED_SECRET}
realm=${TURN_REALM}
server-name=${TURN_REALM}
external-ip=${TURN_EXTERNAL_IP}
listening-port=${TURN_LISTEN_PORT}
min-port=${TURN_RELAY_MIN_PORT}
max-port=${TURN_RELAY_MAX_PORT}
no-multicast-peers
stale-nonce=600
no-tls
log-file=stdout
EOF
chmod 600 /opt/homira-turn/turnserver.conf

docker pull "${TURN_IMAGE}"
docker rm -f homira-coturn >/dev/null 2>&1 || true

docker run -d   --name homira-coturn   --restart unless-stopped   --network host   -v /opt/homira-turn/turnserver.conf:/etc/coturn/turnserver.conf:ro   "${TURN_IMAGE}"   -c /etc/coturn/turnserver.conf >/dev/null

sleep 4

docker ps --format '{{.Names}}' | grep -qx homira-coturn
ss -lnut | grep -q ":${TURN_LISTEN_PORT} "

echo HOMIRA_TURN_BOOTSTRAP_OK
REMOTE_SCRIPT

  log "Installing/configuring Coturn inside the VM with Azure Run Command..."
  BOOTSTRAP_RESULT="$(
    az vm run-command invoke       --resource-group "$RG" --name "$VM" --command-id RunShellScript       --scripts @"$BOOTSTRAP_FILE"       --parameters "$TURN_SHARED_SECRET" "$PUBLIC_IP" "$TURN_REALM" "$TURN_LISTEN_PORT" "$TURN_RELAY_MIN_PORT" "$TURN_RELAY_MAX_PORT" "$TURN_IMAGE"       --query "value[0].message" -o tsv
  )"

  rm -f "$BOOTSTRAP_FILE"

  grep -q "HOMIRA_TURN_BOOTSTRAP_OK" <<<"$BOOTSTRAP_RESULT" || {
    echo "$BOOTSTRAP_RESULT" >&2
    die "Coturn bootstrap did not report success."
  }

  log "Coturn is running on Azure."

  TURN_URLS="[\"turn:${PUBLIC_IP}:${TURN_LISTEN_PORT}\",\"turn:${PUBLIC_IP}:${TURN_LISTEN_PORT}?transport=tcp\"]"

  if [[ -n "${SUPABASE_ACCESS_TOKEN:-}" ]]; then
    command -v npx >/dev/null 2>&1 || die "SUPABASE_ACCESS_TOKEN was provided, but npx is unavailable."

    log "Sending TURN configuration directly to Supabase Edge Function secrets..."
    npx --yes "supabase@${SUPABASE_CLI_VERSION}" secrets set       --project-ref "$SUPABASE_PROJECT_REF"       "HOMIRA_TURN_SHARED_SECRET=${TURN_SHARED_SECRET}"       "HOMIRA_TURN_URLS=${TURN_URLS}"       "HOMIRA_TURN_TTL_SECONDS=${TURN_TTL_SECONDS}"       >/dev/null

    log "Supabase TURN secrets updated. The permanent TURN secret was not printed or written to a local plaintext file."
  else
    FALLBACK_SECRET_FILE="${HOME}/.homira-turn-supabase.env"
    umask 077
    cat > "$FALLBACK_SECRET_FILE" <<EOF
HOMIRA_TURN_SHARED_SECRET=${TURN_SHARED_SECRET}
HOMIRA_TURN_URLS=${TURN_URLS}
HOMIRA_TURN_TTL_SECONDS=${TURN_TTL_SECONDS}
EOF
    chmod 600 "$FALLBACK_SECRET_FILE"

    warn "SUPABASE_ACCESS_TOKEN was not set, so automatic Supabase sync was skipped."
    warn "A mode-600 secrets file was created at: ${FALLBACK_SECRET_FILE}"
    warn "Use it directly with 'supabase secrets set --env-file ... --project-ref ${SUPABASE_PROJECT_REF}', then delete the file."
  fi
else
  warn "Existing TURN server was preserved. No secret was read from the VM and no Supabase secret was changed."
  warn "Set ROTATE_TURN_SECRET=1 only if you intentionally want to rotate both Coturn and Supabase credentials."
fi

log "Verifying VM/container status..."
VERIFY="$(
  az vm run-command invoke     --resource-group "$RG" --name "$VM" --command-id RunShellScript     --scripts 'set -e; docker ps --format "{{.Names}}" | grep -qx homira-coturn; ss -lnut | grep -q ":3478 "; echo HOMIRA_TURN_RUNNING'     --query "value[0].message" -o tsv
)"

grep -q "HOMIRA_TURN_RUNNING" <<<"$VERIFY" || die "Final Coturn verification failed."

echo
echo "============================================================"
echo " Homira Azure TURN provisioned"
echo "============================================================"
echo "Resource group : $RG"
echo "VM             : $VM"
echo "Size           : $SIZE"
echo "Region         : $REGION"
echo "Public IPv4    : $PUBLIC_IP"
echo "TURN           : turn:${PUBLIC_IP}:${TURN_LISTEN_PORT}"
echo "TURN TCP       : turn:${PUBLIC_IP}:${TURN_LISTEN_PORT}?transport=tcp"
echo "Relay UDP      : ${TURN_RELAY_MIN_PORT}-${TURN_RELAY_MAX_PORT}"
echo "Supabase ref   : $SUPABASE_PROJECT_REF"
echo
echo "The permanent TURN shared secret is intentionally NOT displayed."
echo
echo "Next Homira test:"
echo "  Install the relay-only Homira APK on two devices and place a call."
echo "  Because that build uses ICE transport policy RELAY, a successful call"
echo "  proves the Azure Coturn path is actually carrying WebRTC media."
echo "============================================================"
