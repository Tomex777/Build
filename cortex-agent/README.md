# Cortex Agent

Small Node.js 24 service that gives the Cortex Android app live access to Night on an Azure Linux VM without coupling Cortex to the Night process itself.

It stays separate from `night.service`, so files, metrics and logs remain reachable while Night is stopped or restarting.

## API

All routes require `Authorization: Bearer <CORTEX_AGENT_TOKEN>`.

- `GET /api/cortex/host/status` — Night service state plus live CPU, RAM, disk and VM uptime
- `GET /api/cortex/host/logs?limit=200` — `journalctl` output for Night
- `POST /api/cortex/host/power` — `start`, `stop` or `restart` Night
- `GET /api/cortex/host/files?path=/` — list files inside the Night project root
- `GET /api/cortex/host/files/content?path=index.js` — read a text file
- `POST /api/cortex/host/files/content` — atomically write a text file

The agent refuses paths outside the configured project root and hides `.env`, `.git`, `.ssh` and common private-key filenames from the file API.

## Environment

```text
CORTEX_AGENT_TOKEN=<strong random token, minimum 24 characters>
NIGHT_ROOT=/opt/night
NIGHT_SERVICE=night.service
NIGHT_ENTRY=index.js
NIGHT_START_COMMAND=node index.js
HOST=127.0.0.1
PORT=47831
```

`HOST=127.0.0.1` is intentional. Put the agent behind an authenticated HTTPS endpoint/reverse proxy rather than exposing its raw HTTP port to the internet. The Android Azure adapter requires an `https://` Cortex Agent URL.

## systemd

`cortex-agent.service` is supplied as the initial VM unit. It currently runs as root because it must control `night.service`; the API itself remains constrained to `NIGHT_ROOT`. The next hardening step is to move service control behind a narrowly scoped privilege rule and run the agent as a dedicated user.

## CI

`.github/workflows/cortex-agent.yml` boots the agent on Node 24 and verifies authenticated status + file read/write/list behavior and unauthenticated rejection.
