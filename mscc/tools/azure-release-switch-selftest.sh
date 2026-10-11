#!/usr/bin/env bash
set -euo pipefail

# Exercise the installer's actual atomic release switch without touching /opt,
# systemd, authentication state or any live Azure account.
INSTALLER="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/install-azure.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

awk '
  /^switch_release\(\) \{/ { inside=1 }
  inside { print }
  inside && /^\}/ { exit }
' "$INSTALLER" > "$TEST_ROOT/switch.sh"
test -s "$TEST_ROOT/switch.sh"
bash -n "$TEST_ROOT/switch.sh"

# Source only the pure file/symlink helper, never the full installer.
source "$TEST_ROOT/switch.sh"
BASE="$TEST_ROOT/opt/mscc"
mkdir -p "$BASE/releases/old" "$BASE/releases/next"
echo original > "$BASE/releases/old/marker"
echo new > "$BASE/releases/next/marker"
ln -s "$BASE/releases/old" "$BASE/current"

switch_release "$BASE/releases/next"
test "$(readlink -f "$BASE/current")" = "$BASE/releases/next"
test -f "$BASE/releases/old/marker"

switch_release "$BASE/releases/old"
test "$(readlink -f "$BASE/current")" = "$BASE/releases/old"
test -f "$BASE/releases/next/marker"


# Prove the actual EXIT rollback handler restores the old release on an
# activation failure. Mock sudo so no host services or protected files change.
awk '
  /^rollback_on_error\(\) \{/ { inside=1 }
  inside { print }
  inside && /^\}/ { exit }
' "$INSTALLER" >> "$TEST_ROOT/switch.sh"
source "$TEST_ROOT/switch.sh"

sudo() {
  if [[ "$*" == "systemctl restart mscc.service" ]]; then
    printf 'restart\n' >> "$TEST_ROOT/mock-systemctl.txt"
  elif [[ "$*" == "systemctl stop mscc.service" ]]; then
    printf 'stop\n' >> "$TEST_ROOT/mock-systemctl.txt"
  else
    echo "Unexpected privileged command in rollback test" >&2
    return 1
  fi
}

PREVIOUS_RELEASE="$BASE/releases/old"
ACTIVATED=1
switch_release "$BASE/releases/next"
if ( set -e; trap rollback_on_error EXIT; false ); then
  echo "Expected simulated failed deployment to exit non-zero" >&2
  exit 1
fi
test "$(readlink -f "$BASE/current")" = "$BASE/releases/old"
grep -Fq 'restart' "$TEST_ROOT/mock-systemctl.txt"

# A successful activation must not roll back merely because the EXIT trap runs.
switch_release "$BASE/releases/next"
ACTIVATED=0
( set -e; trap rollback_on_error EXIT; true )
test "$(readlink -f "$BASE/current")" = "$BASE/releases/next"
test -f "$BASE/releases/old/marker"

# A failed *first* deployment must stop the broken service, not silently
# report success with a nonexistent previous release.
PREVIOUS_RELEASE=""
ACTIVATED=1
if ( set -e; trap rollback_on_error EXIT; false ); then
  echo "Expected failed first deployment to exit non-zero" >&2
  exit 1
fi
grep -Fq 'stop' "$TEST_ROOT/mock-systemctl.txt"

grep -Fq 'trap rollback_on_error EXIT' "$INSTALLER"
grep -Fq 'ACTIVATED=1' "$INSTALLER"
grep -Fq 'ACTIVATED=0' "$INSTALLER"
grep -Fq 'test ! -e "$RELEASE"' "$INSTALLER"
if grep -Fq 'rm -rf "$RELEASE"' "$INSTALLER"; then
  echo 'ERROR: installer must never delete a release directory' >&2
  exit 1
fi
echo 'PASS MSCC atomic release switch, EXIT rollback, and state preservation tests'
