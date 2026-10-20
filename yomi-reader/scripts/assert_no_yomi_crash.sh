#!/usr/bin/env bash
# Fail only for a fatal crash in Yomi. Android emulators sometimes log unrelated
# platform crashes (e.g. com.android.phone's SatelliteController); those must
# not invalidate an otherwise completed reader acceptance run.
set -euo pipefail
log=${1:?Usage: assert_no_yomi_crash.sh <threadtime-logcat-file> [application-id]}
package=${2:-app.yomi.reader.dev}

# AndroidRuntime emits a "Process: PACKAGE, PID: N" marker next to every Java
# FATAL EXCEPTION. A generic FATAL EXCEPTION grep also matches system services.
if grep -F -e "AndroidRuntime: Process: $package," -e "AndroidRuntime: Process: $package " "$log"; then
    echo "A fatal AndroidRuntime crash occurred inside $package" >&2
    exit 1
fi

# A native tombstone contains Cmdline even when no Java exception exists.
# Only match the named app itself, not another component mentioning the app.
if grep -F -e "DEBUG   : Cmdline: $package" -e "DEBUG: Cmdline: $package" "$log"; then
    echo "A native process crash occurred inside $package" >&2
    exit 1
fi
echo "PASS: no $package process crash in logcat"
