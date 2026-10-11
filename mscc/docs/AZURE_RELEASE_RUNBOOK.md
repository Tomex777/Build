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

## Cortex Agent and Android connection (one-time installation)

MSCC is the WhatsApp backend. The Android app reaches its account dashboard **through Cortex Agent**, a separate service. A healthy `mscc.service` alone does not mean that the mobile dashboard is reachable.

On the **same Azure VM** after MSCC is healthy:

1. Review the trusted `cortex-production-finalization` commit you intend to run, plus the current `cortex-agent/README.md`, `cortex-agent/install.sh` and `cortex-agent/install-azure-ip-https.sh` scripts. They manage systemd, ACLs, nginx, TLS and firewall-facing HTTPS.
2. Pin a full 40-character `CORTEX_SOURCE_REF` commit, download the entire archive to a temporary restricted directory and run `sudo bash cortex-agent/install.sh` from the extracted source. Do not execute a branch head that can change between review and installation.
3. If the VM has a **static public IPv4** and no conflicting nginx site configuration, use the reviewed `install-azure-ip-https.sh` to set up the Android HTTPS endpoint. The helper changes nginx's default site, so review it carefully if the VM hosts other websites.
4. In the Azure network security group, permit TCP **443** for the app and TCP **80** for ACME certificate issuance/renewal. Restrict SSH access. Never expose the private Cortex Agent **47831** or MSCC **8787/8788** ports publicly.
5. Save the generated Cortex Agent bearer token securely on first installation. **Never paste it into GitHub, CI logs, or support chats.** Configure Cortex Android with the trusted HTTPS URL and that token.
6. Confirm `systemctl is-active cortex-agent`, `systemctl is-active mscc`, HTTPS certificate validity, and the authenticated Cortex host/status and MSCC pairing screens from the Android device.

Example pinned-source preparation (on the VM; replace the placeholder with an already reviewed commit SHA):

```bash
set -euo pipefail
export CORTEX_SOURCE_REF='<TESTED_CORTEX_COMMIT_SHA>'
[[ "$CORTEX_SOURCE_REF" =~ ^[[:xdigit:]]{40}$ ]] || exit 1
work="$(mktemp -d)"
chmod 700 "$work"
curl -fsSL "https://github.com/Tomex777/Build/archive/$CORTEX_SOURCE_REF.tar.gz" -o "$work/cortex.tar.gz"
mkdir "$work/source"
tar -xzf "$work/cortex.tar.gz" -C "$work/source" --strip-components=1
bash -n "$work/source/cortex-agent/install.sh" "$work/source/cortex-agent/install-azure-ip-https.sh"
sudo bash "$work/source/cortex-agent/install.sh"
# Review the HTTPS helper and confirm the VM's static Azure public IPv4 first:
# sudo bash "$work/source/cortex-agent/install-azure-ip-https.sh" <AZURE_STATIC_PUBLIC_IPV4>
```

Keep the temporary checkout until you have completed and inspected the HTTPS setup, then remove it. The Cortex Agent installation is separate from the MSCC application installer. Once installed, the MSCC atomic upgrade script refreshes Cortex Agent ACL permissions on each **new** release directory so that file editing remains available across upgrades.

A successful local simulated Cortex↔MSCC contract test proves HTTP compatibility but cannot replace an actual on-device connectivity, pairing and real-message test.
