#!/usr/bin/env bash
set -euo pipefail
root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT

previous="$root/previous"
candidate="$root/candidate"
mkdir -p "$previous/node_modules/test-package" "$candidate" "$root/bin"
printf 'module.exports=1\n' > "$previous/node_modules/test-package/index.js"
cat > "$previous/package.json" <<'JSON'
{"type":"module","dependencies":{"test-package":"1.0.0"},"scripts":{"check":"node old-test"}}
JSON
cat > "$candidate/package.json" <<'JSON'
{"type":"module","scripts":{"check":"node new-test"},"dependencies":{"test-package":"1.0.0"}}
JSON

# A code-only update preserves exact native modules without npm registry use.
bash "$(dirname "$0")/install-node-deps.sh" "$candidate" "$previous"
test -f "$candidate/node_modules/test-package/index.js"
test ! -L "$candidate/node_modules"
test -f "$previous/node_modules/test-package/index.js"

# Different runtime dependencies MUST NOT reuse the prior release's modules.
rm -rf "$candidate/node_modules"
printf '%s\n' '{"dependencies":{"test-package":"2.0.0"}}' > "$candidate/package.json"
cat > "$root/bin/npm" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" > "$MSCC_NPM_ARGS_FILE"
SH
chmod +x "$root/bin/npm"
export MSCC_NPM_ARGS_FILE="$root/npm-args"
PATH="$root/bin:$PATH" bash "$(dirname "$0")/install-node-deps.sh" "$candidate" "$previous"
test -s "$MSCC_NPM_ARGS_FILE"
grep -Fq -- '--fetch-timeout=30000' "$MSCC_NPM_ARGS_FILE"
grep -Fq -- '--loglevel=verbose' "$MSCC_NPM_ARGS_FILE"
grep -Fq -- '--prefix' "$MSCC_NPM_ARGS_FILE"

# No prior release must use the bounded npm path, never a bogus copy.
rm -f "$MSCC_NPM_ARGS_FILE"
PATH="$root/bin:$PATH" bash "$(dirname "$0")/install-node-deps.sh" "$candidate" ""
test -s "$MSCC_NPM_ARGS_FILE"
echo "PASS: MSCC deployment reuses matching dependencies; cold install bounded and logged"
