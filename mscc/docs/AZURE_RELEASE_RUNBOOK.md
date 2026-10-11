# MSCC / Cortex — Azure release operations

This is an **operator-run procedure**. Repository CI verifies code; it does not demonstrate that a real Azure VM, WhatsApp session, or Cortex Agent has been deployed. Never put WhatsApp auth files, account state, `/etc/mscc.env`, pairing codes, or SSH keys in GitHub issues, logs, or CI artifacts.

## Requirements

- SSH access to the intended Azure Linux VM with `sudo`.
- Back up or snapshot the VM before changes. Ensure sufficient free disk space for two application versions and the snapshot.
- Node 24, `npm`, `curl`, `tar`, and `systemd` installed. Verify the target service and directory layout before running any installer.
- A *reviewed* full Git commit SHA of the tested `mscc-azure` branch. Pin **both** installer and application checkout to the same SHA. Do not deploy a moving branch head.
- Cortex Agent is configured to reach the private MSCC loopback service on its expected local bridge port. Loopback endpoints are not meant to be exposed to the Internet.

## Pre-deployment (on the Azure VM)

```bash
set -euo pipefail
umask 077
sudo systemctl status mscc --no-pager || true
df -h /opt /var
PREVIOUS_RELEASE="$(readlink -f /opt/mscc/current 2>/dev/null || true)"
printf 'Previous release directory: %s\n' "${PREVIOUS_RELEASE:-none}"
# Optional before first deployment, if state is absent:
sudo test -d /var/lib/mscc && echo 'MSCC state directory exists'
sudo test -f /etc/mscc.env && echo 'Private MSCC environment exists'
```

For a running production service, make a VM/disk snapshot or consistent backup of `/var/lib/mscc` and `/etc/mscc.env` **without printing or uploading either**. Auth directories and the shared database must survive upgrades. Treat backups as highly sensitive.

## Pin and deploy the tested commit

Replace the placeholder with the 40-character commit SHA of the reviewed `mscc-azure` commit, then execute the commands *on the VM*:

```bash
set -euo pipefail
export MSCC_SOURCE_REF='<TESTED_MSCC_COMMIT_SHA>'
[[ "$MSCC_SOURCE_REF" =~ ^[[:xdigit:]]{40}$ ]] || { echo 'Invalid deployment commit SHA'; exit 1; }
curl -fsSL "https://raw.githubusercontent.com/Tomex777/Build/${MSCC_SOURCE_REF}/mscc/install-azure.sh" -o /tmp/mscc-install-azure.sh
bash -n /tmp/mscc-install-azure.sh
bash /tmp/mscc-install-azure.sh
```

The installer stages the chosen source in a new, separate release directory. It does not remove the currently running application directory. Once dependencies and checks pass, it switches `/opt/mscc/current` atomically, restarts `mscc.service`, checks its health, and runs a **read-only** Cortex/MSCC account contract smoke test. On failure after the switch, it attempts to restore the previous version.

**Do not reconfigure or remove existing WhatsApp sessions to repair a failed deployment.** Review the service logs first and avoid placing those logs in public tickets without scrubbing credentials and account identifiers.

## Validate after deployment

```bash
sudo systemctl is-active --quiet mscc
curl -fsS http://127.0.0.1:8787/health
node /opt/mscc/current/tools/azure-smoke.mjs
readlink -f /opt/mscc/current
```

The smoke test checks stable session IDs, Account A ownership and fixed CC destination, explicit bot profiles, activity response, and the private API. It reports counts, not sensitive identifiers. It does **not** demonstrate that WhatsApp has authenticated, messages have been delivered, or the Android app reaches Cortex over the public network.

In the Cortex Android application, use the existing configured Cortex server address and access token. Verify account list, account rename, bot-profile changes, reconnection, pairing-code and explicitly chosen QR flows. Perform a consented send/receive check using non-sensitive test accounts before declaring production ready.

## Manual rollback if necessary

Only after diagnosing a failed deployment and identifying a known-good `PREVIOUS_RELEASE` from the pre-deployment record:

```bash
set -euo pipefail
PREVIOUS_RELEASE='<PREVIOUS_RELEASE_PATH>'
test -d "$PREVIOUS_RELEASE"
sudo systemctl stop mscc
candidate="/opt/mscc/.current-manual-rollback-$$"
sudo ln -s "$PREVIOUS_RELEASE" "$candidate"
sudo mv -Tf "$candidate" /opt/mscc/current
sudo systemctl start mscc
sudo systemctl is-active --quiet mscc
```

If state formats changed between versions, consult the backup/restore plan before downgrading. Never overwrite WhatsApp auth or shared-storage data without an explicit recovery decision.

## Android release gate

The production-signed Cortex APK is built by `Cortex Android Release Readiness` on the trusted `cortex-production-finalization` branch. Confirm successful `apksigner verify` output and a matching SHA-256 digest before distributing it. The debug APK and any CI QA/debug-signed `INSTALLABLE` variant are **not** substitutes for the production-signed APK.

The dedicated `Cortex Android` full-emulator suite must also pass on both API 26 and API 36. A successful compilation alone is insufficient.
