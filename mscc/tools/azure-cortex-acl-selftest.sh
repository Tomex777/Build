#!/usr/bin/env bash
set -euo pipefail

# Extract the exact production permission helper, without executing install-azure.sh.
INSTALLER="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/install-azure.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT
awk '
  /^grant_cortex_release_access\(\) \{/ { copying=1 }
  copying { print }
  copying && /^\}/ { exit }
' "$INSTALLER" > "$TEST_ROOT/acl-helper.sh"
test -s "$TEST_ROOT/acl-helper.sh"
bash -n "$TEST_ROOT/acl-helper.sh"

RELEASE="$TEST_ROOT/release"
mkdir -p "$TEST_ROOT/bin" "$RELEASE/nested" "$RELEASE/node_modules/package" "$RELEASE/.ssh" "$RELEASE/auth" "$RELEASE/.cortex"
touch "$RELEASE/editable.js" "$RELEASE/nested/update.js"
touch "$RELEASE/node_modules/package/private.js" "$RELEASE/.ssh/id_rsa" "$RELEASE/auth/creds.json" "$RELEASE/.env" "$RELEASE/.cortex/secret"
ACL_TEST_LOG="$TEST_ROOT/acl.log"
export ACL_TEST_LOG RELEASE

cat > "$TEST_ROOT/bin/id" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == '-u' && "${2:-}" == 'cortex-agent' ]]; then echo 10001; exit 0; fi
exec /usr/bin/id "$@"
EOF
cat > "$TEST_ROOT/bin/setfacl" <<'EOF'
#!/usr/bin/env bash
echo 'A mocked setfacl binary; never modify filesystem ACLs in CI' >/dev/null
EOF
cat > "$TEST_ROOT/bin/sudo" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == 'setfacl' && "$2" == '-m' ]]
entry="$3"
shift 3
for path in "$@"; do
  printf '%s|%s\n' "$entry" "$path" >> "$ACL_TEST_LOG"
done
EOF
chmod +x "$TEST_ROOT/bin/id" "$TEST_ROOT/bin/setfacl" "$TEST_ROOT/bin/sudo"
export PATH="$TEST_ROOT/bin:$PATH"

source "$TEST_ROOT/acl-helper.sh"
grant_cortex_release_access

grep -Fxq "u:cortex-agent:rwX|$RELEASE/editable.js" "$ACL_TEST_LOG"
grep -Fxq "u:cortex-agent:rwX|$RELEASE/nested/update.js" "$ACL_TEST_LOG"
grep -Fxq "d:u:cortex-agent:rwX|$RELEASE/nested" "$ACL_TEST_LOG"
grep -Fxq "d:u:cortex-agent:rwX|$RELEASE" "$ACL_TEST_LOG"

if grep -E '/(node_modules|auth|\.ssh|\.cortex)/|/\.env($|/)' "$ACL_TEST_LOG"; then
  echo "Cortex Agent received ACL grants to protected release paths" >&2
  exit 1
fi
echo 'PASS Cortex Agent release ACL refresh and protected-path isolation'
