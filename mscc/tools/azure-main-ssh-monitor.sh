#!/usr/bin/env bash
set -euo pipefail

for key in MSCC_SSH_HOST MSCC_SSH_USER MSCC_SSH_KEY MSCC_SSH_KNOWN_HOSTS; do
  if [[ -z "${!key:-}" ]]; then
    echo "Missing required mscc-production secret: $key" >&2
    exit 2
  fi
done
HOST="$MSCC_SSH_HOST"
USER_NAME="$MSCC_SSH_USER"
PORT="${MSCC_SSH_PORT:-22}"
[[ "$HOST" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ ]] || { echo "Invalid SSH hostname." >&2; exit 2; }
[[ "$USER_NAME" =~ ^[a-z_][a-z0-9_-]*$ ]] || { echo "Invalid SSH username." >&2; exit 2; }
[[ "$PORT" =~ ^[0-9]{1,5}$ ]] && ((10#$PORT >= 1 && 10#$PORT <= 65535)) || { echo "Invalid SSH port." >&2; exit 2; }

umask 077
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf '%s\n' "$MSCC_SSH_KEY" > "$tmp/monitor_key"
printf '%s\n' "$MSCC_SSH_KNOWN_HOSTS" > "$tmp/known_hosts"
chmod 600 "$tmp/monitor_key" "$tmp/known_hosts"
ssh-keygen -lf "$tmp/known_hosts" >/dev/null 2>&1 || {
  echo "Pinned Azure SSH host key is invalid." >&2
  exit 2
}

for sample in 1 2 3 4 5 6; do
  if ! raw="$(ssh -T -F /dev/null \
    -o BatchMode=yes -o StrictHostKeyChecking=yes \
    -o "UserKnownHostsFile=$tmp/known_hosts" \
    -o IdentitiesOnly=yes -o IdentityAgent=none \
    -o ConnectTimeout=20 -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
    -i "$tmp/monitor_key" -p "$PORT" "$USER_NAME@$HOST" mscc-main-diagnostics 2>"$tmp/ssh_error")"; then
    echo "Read-only Main diagnostics command failed (sample $sample)." >&2
    exit 1
  fi

  safe="$(printf '%s' "$raw" | node --input-type=commonjs -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      try {
        const row = JSON.parse(input);
        if (row.account !== "Main" || typeof row.connected !== "boolean" ||
            typeof row.registered !== "boolean" || typeof row.paused !== "boolean" ||
            typeof row.status !== "string" || !Array.isArray(row.recentEvents) ||
            !row.disconnectsByCode || typeof row.disconnectsByCode !== "object") process.exit(2);
        const output = {
          observedAt: String(row.observedAt || ""),
          account: "Main",
          connected: row.connected,
          registered: row.registered,
          paused: row.paused,
          status: row.status,
          lastDisconnectCode: Number.isFinite(row.lastDisconnectCode) ? row.lastDisconnectCode : null,
          disconnectsByCode: Object.fromEntries(Object.entries(row.disconnectsByCode)
            .filter(([code, count]) => /^\d{1,4}$/.test(code) && Number.isInteger(count))
            .slice(0, 8)),
          recentEvents: row.recentEvents.slice(0, 8).map(event => ({
            at: String(event.at || ""),
            action: ["account.connected", "account.disconnected", "account.reconnect-scheduled", "account.reconnect-failed"].includes(event.action)
              ? event.action : "unknown",
            ...(Number.isFinite(event.reasonCode) ? { reasonCode: event.reasonCode } : {}),
            ...(["intentional-logout", "stream-error", "server-failure", "connection-replaced", "transport-close", "other"].includes(event.origin) ? { origin: event.origin } : {}),
            ...(["device-removed", "connection-replaced", "other-conflict", "not-authorized", "bad-session", "policy-violation", "unclassified"].includes(event.protocolDetail) ? { protocolDetail: event.protocolDetail } : {})
          })),
          accountComparison: row.accountComparison && Array.isArray(row.accountComparison.accounts) &&
            row.accountComparison.accounts.length <= 50
            ? {
                verdict: ["main-revoked-while-linked-connected","multiple-connected","inconclusive"].includes(row.accountComparison.verdict)
                  ? row.accountComparison.verdict : "inconclusive",
                accounts: row.accountComparison.accounts.map((a,i) => ({
                  account: i === 0 ? "Main" : "Linked #" + i,
                  status: ["connected","connecting","reconnecting","auth-invalid","offline","paused","pairing","disabled","unknown"].includes(a.status)
                    ? a.status : "unknown",
                  connected: a.connected === true,
                  registered: a.registered === true,
                  paused: a.paused === true,
                  lastDisconnectCode: Number.isInteger(a.lastDisconnectCode) ? a.lastDisconnectCode : null,
                  recentEvents: (Array.isArray(a.recentEvents) ? a.recentEvents : []).slice(0,8).flatMap(e => {
                    if (!["account.connected","account.disconnected","account.reconnect-failed","account.reconnect-scheduled"].includes(e.action)) return [];
                    return [{
                      at: typeof e.at === "string" && /^\\d{4}-\\d{2}-\\d{2}T/.test(e.at) ? e.at.slice(0,48) : "",
                      action: e.action,
                      ...(Number.isInteger(e.code) ? { code:e.code } : {}),
                      ...(["intentional-logout","stream-error","server-failure","connection-replaced","transport-close","other"].includes(e.origin) ? { origin:e.origin } : {}),
                      ...(["device-removed","connection-replaced","other-conflict","not-authorized","bad-session","policy-violation","unclassified"].includes(e.protocolDetail) ? { protocolDetail:e.protocolDetail } : {})
                    }];
                  })
                }))
              } : null
        };
        process.stdout.write(JSON.stringify(output));
      } catch { process.exit(2); }
    });
  ')" || { echo "Main diagnostics returned an invalid sanitized report." >&2; exit 1; }

  printf 'Observation %s/6: %s\n' "$sample" "$safe"
  if [[ "$sample" -lt 6 ]]; then sleep 30; fi
done
