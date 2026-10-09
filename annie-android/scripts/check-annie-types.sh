#!/usr/bin/env sh
# L5 (types): the generated annie.d.ts must type a sample package using every operation,
# and the negative sample must fail exactly where marked. Needs `tsc` (npm i -g typescript) on PATH.
set -eu
cd "$(dirname "$0")/../docs/types-sample"
tsc -p tsconfig.json
echo "annie typings OK"
