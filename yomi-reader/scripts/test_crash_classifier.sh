#!/usr/bin/env bash
set -euo pipefail
script_dir=$(dirname "${BASH_SOURCE[0]}")
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/platform.txt" <<'EOF'
10-09 22:34:28.838  4509  4644 E AndroidRuntime: FATAL EXCEPTION: SatelliteController
10-09 22:34:28.838  4509  4644 E AndroidRuntime: Process: com.android.phone, PID: 4509
10-09 22:34:28.840  4509  4644 E AndroidRuntime: java.lang.IllegalStateException: Not initialized
10-09 22:34:29.100  3500  3500 I YomiReader: page-image-ready name=1.png
EOF
bash "$script_dir/assert_no_yomi_crash.sh" "$tmp/platform.txt" >/dev/null

cat > "$tmp/yomi.txt" <<'EOF'
10-09 22:34:28.838  4509  4644 E AndroidRuntime: FATAL EXCEPTION: main
10-09 22:34:28.838  4509  4644 E AndroidRuntime: Process: app.yomi.reader.dev, PID: 4509
10-09 22:34:28.840  4509  4644 E AndroidRuntime: java.lang.IllegalArgumentException: Bad archive
EOF
if bash "$script_dir/assert_no_yomi_crash.sh" "$tmp/yomi.txt" >/dev/null 2>&1; then
    echo "FAIL: a genuine Yomi Java crash was not rejected" >&2
    exit 1
fi

cat > "$tmp/native.txt" <<'EOF'
10-09 22:34:28.838   100   100 F DEBUG   : *** *** ***
10-09 22:34:28.838   100   100 F DEBUG   : Cmdline: app.yomi.reader.dev
10-09 22:34:28.838   100   100 F DEBUG   : signal 11 (SIGSEGV)
EOF
if bash "$script_dir/assert_no_yomi_crash.sh" "$tmp/native.txt" >/dev/null 2>&1; then
    echo "FAIL: a genuine Yomi native crash was not rejected" >&2
    exit 1
fi
echo "PASS: unrelated Android system crashes ignored; Yomi Java/native crashes rejected"
