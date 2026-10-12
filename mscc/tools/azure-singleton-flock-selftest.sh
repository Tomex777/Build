#!/usr/bin/env bash
set -euo pipefail

# The same lock may not be held by two Night runtimes simultaneously,
# but independent services can keep their own locks.
command -v flock >/dev/null
tmp="$(mktemp -d)"
holder=""
cleanup() {
  if [[ -n "$holder" ]]; then kill "$holder" 2>/dev/null || true; wait "$holder" 2>/dev/null || true; fi
  rm -rf "$tmp"
}
trap cleanup EXIT

flock -n "$tmp/main.lock" bash -c 'printf ready > "$1/ready"; sleep 2' _ "$tmp" &
holder=$!
for _ in {1..40}; do
  if [[ -f "$tmp/ready" ]]; then break; fi
  sleep 0.05
done
[[ -f "$tmp/ready" ]] || { echo 'First MSCC lock holder did not start' >&2; exit 1; }

if flock -n "$tmp/main.lock" true; then
  echo 'Duplicate MSCC runtime unexpectedly obtained the same lock' >&2
  exit 1
fi
flock -n "$tmp/another-service.lock" true

wait "$holder"
holder=""
flock -n "$tmp/main.lock" true

INSTALLER="$(cd "$(dirname "$0")/.." && pwd)/install-azure.sh"
grep -Fq 'command -v flock >/dev/null' "$INSTALLER"
grep -Fq 'ExecStart=$(command -v flock) -n -E 75 $STATE/data/.mscc-runtime.lock' "$INSTALLER"
echo 'PASS exclusive MSCC runtime lock and independent service locks'
