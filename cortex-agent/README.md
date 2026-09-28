# Cortex Agent

Small Node.js 24 service that gives the Cortex Android app live access to MSCC on an Azure Linux VM without coupling Cortex to the MSCC process itself.

It stays separate from `mscc.service`, so files, metrics and logs remain reachable while MSCC is stopped or restarting.

## API

All routes require `Authorization: Bearer <CORTEX_AGENT_TOKEN>`.

- `GET /api/cortex/host/status` — configured MSCC service state plus live CPU, RAM, disk and VM uptime
- `GET /api/cortex/host/logs?limit=200` — redacted `journalctl` output for the configured MSCC service
- `POST /api/cortex/host/power` — `start`, `stop` or `restart` Night
- `GET /api/cortex/host/files?path=/` — list files inside the configured MSCC project root
- `GET /api/cortex/host/files/content?path=index.js` — read a text file
- `POST /api/cortex/host/files/content` — atomically write a text file

The agent refuses paths outside the configured project root and hides `.env`, `.git`, `.ssh` and common private-key filenames from the file API.

## Environment

```text
CORTEX_AGENT_TOKEN=<strong random token, minimum 24 characters>
CORTEX_PROJECT_ROOT=/opt/mscc/current
CORTEX_SERVICE=mscc.service
CORTEX_ENTRY=index.js
CORTEX_START_COMMAND=node --max-old-space-size=192 index.js
CORTEX_STATE_DIR=/var/lib/cortex
HOST=127.0.0.1
PORT=47831
```

`HOST=127.0.0.1` is intentional. Put the agent behind an authenticated HTTPS endpoint/reverse proxy rather than exposing its raw HTTP port to the internet. The Cortex Android app requires an `https://` Cortex Agent URL.

## systemd

`cortex-agent.service` is supplied as the initial VM unit. It currently runs as root because it must control `night.service`; the API itself remains constrained to `NIGHT_ROOT`. The next hardening step is to move service control behind a narrowly scoped privilege rule and run the agent as a dedicated user.

## CI

`.github/workflows/cortex-agent.yml` boots the agent on Node 24 and verifies authenticated status + file read/write/list behavior and unauthenticated rejection.


## Domainless Azure HTTPS

The Cortex Android app is the production client; no browser-based replacement is required.

For an Azure VM with a **static public IPv4 address**, run the Cortex Agent on
`127.0.0.1:47831` and expose only the agent through HTTPS on port 443. The
`install-azure-ip-https.sh` helper configures nginx as a reverse proxy and
requests a publicly trusted Let's Encrypt short-lived IP-address certificate
using Certbot's webroot flow.

Requirements:

- Azure public IP allocation is static.
- Azure NSG allows inbound TCP 80 for ACME HTTP-01 validation/renewal.
- Azure NSG allows inbound TCP 443 for Cortex.
- Do not expose ports 47831 or 8788 publicly.
- Cortex stores the resulting URL as `https://<public-ip>` and authenticates
  with the existing Cortex Agent bearer token.

Run after `install.sh`:

```bash
sudo bash cortex-agent/install-azure-ip-https.sh <STATIC_PUBLIC_IPV4>
```

The helper installs Certbot 5.4+ in an isolated virtual environment, obtains a
short-lived IP certificate, configures nginx, installs an automatic renewal
timer, and verifies the authenticated Cortex Agent route without printing the
agent token.


If Azure IMDS exposes the NIC but leaves `publicIpAddress` blank, the installer
falls back to the VM's externally visible IPv4 as a candidate. Confirm that
candidate matches the **Static Public IP** attached to the VM/NIC in Azure.
If it does not, pass the correct static address explicitly:

```bash
sudo bash cortex-agent/install-azure-ip-https.sh <STATIC_PUBLIC_IPV4>
```
