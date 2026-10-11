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

grep -Fq 'trap rollback_on_error EXIT' "$INSTALLER"
grep -Fq 'ACTIVATED=1' "$INSTALLER"
grep -Fq 'ACTIVATED=0' "$INSTALLER"
grep -Fq 'test ! -e "$RELEASE"' "$INSTALLER"
if grep -Fq 'rm -rf "$RELEASE"' "$INSTALLER"; then
  echo 'ERROR: installer must never delete a release directory' >&2
  exit 1
fi
echo 'PASS MSCC atomic release switch and preservation smoke test'
