#!/usr/bin/env bash
set -euo pipefail

# Extract the production installer's actual reuse verifier and exercise it
# without Docker, network access, systemd or a privileged Azure host.
INSTALLER="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/install-azure.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

awk '
  /^verify_existing_source_stack\(\) \{/ { inside=1 }
  inside { print }
  inside && /^\}/ { exit }
' "$INSTALLER" > "$TEST_ROOT/reuse-function.sh"
test -s "$TEST_ROOT/reuse-function.sh"
bash -n "$TEST_ROOT/reuse-function.sh"

cat > "$TEST_ROOT/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
args="$*"
case "$args" in
  *check.torproject.org/api/ip*)
    if [[ "$args" != *"socks5h://127.0.0.1:"* ]]; then exit 1; fi
    if [[ "${TEST_TOR_DOWN:-0}" == "1" ]]; then exit 7; fi
    if [[ "${TEST_TOR_BAD_EXIT:-0}" == "1" ]]; then echo '{"IsTor":false}'; else echo '{"IsTor":true}'; fi
    ;;
  *"/health"*)
    if [[ "$args" != *"http://127.0.0.1:"* ]]; then exit 1; fi
    if [[ "${TEST_FLARE_DOWN:-0}" == "1" ]]; then exit 7; fi
    echo '{"status":"ok"}'
    ;;
  *) echo "Unexpected curl probe: $args" >&2; exit 1 ;;
esac
EOF
chmod +x "$TEST_ROOT/curl"
export PATH="$TEST_ROOT:$PATH"
source "$TEST_ROOT/reuse-function.sh"

verify_existing_source_stack >/dev/null

if ( export TEST_TOR_DOWN=1; verify_existing_source_stack >/dev/null 2>&1 ); then
  echo "ERROR: reuse should reject an unreachable Tor proxy" >&2
  exit 1
fi
if ( export TEST_TOR_BAD_EXIT=1; verify_existing_source_stack >/dev/null 2>&1 ); then
  echo "ERROR: reuse should reject a proxy not confirmed as Tor" >&2
  exit 1
fi
if ( export TEST_FLARE_DOWN=1; verify_existing_source_stack >/dev/null 2>&1 ); then
  echo "ERROR: reuse should reject unavailable FlareSolverr" >&2
  exit 1
fi
if ( export MSCC_TOR_PORT=not-a-port; verify_existing_source_stack >/dev/null 2>&1 ); then
  echo "ERROR: reuse should reject invalid port" >&2
  exit 1
fi
if ( export MSCC_FLARE_PORT=65536; verify_existing_source_stack >/dev/null 2>&1 ); then
  echo "ERROR: reuse should reject out-of-range port" >&2
  exit 1
fi

grep -Fq 'if [ "${MSCC_REUSE_EXISTING_SOURCE_STACK:-0}" = "1" ]; then' "$INSTALLER"
grep -Fq '  verify_existing_source_stack' "$INSTALLER"
# The opt-in path must occur before Docker stack installation and before
# switching the active release symlink or restarting mscc.service.
awk '/^if \[ "\$\{MSCC_REUSE_EXISTING_SOURCE_STACK:-0\}" = "1" \]; then/{reuse=NR}
  /bash "\$RELEASE\/tools\/install-flaresolverr-tor.sh"/{stack=NR}
  /^switch_release "\$RELEASE"/{activate=NR}
  END{exit !(reuse>0 && reuse<stack && stack<activate)}' "$INSTALLER"
echo 'PASS existing Tor/FlareSolverr reuse, outage guards, and no service replacement'
