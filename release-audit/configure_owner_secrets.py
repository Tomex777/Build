#!/usr/bin/env python3
"""Upload one recovered identity through an owner-authenticated gh CLI.

Passwords are read only from RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD.
No key generation, key rotation, or secret values are printed.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    records = json.loads(Path(__file__).with_name('release-identities.json').read_text())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', choices=records, required=True)
    parser.add_argument('--keystore', type=Path, required=True)
    args = parser.parse_args()
    record = records[args.app]
    for name in ('RELEASE_STORE_PASSWORD', 'RELEASE_KEY_PASSWORD'):
        if not os.environ.get(name):
            raise SystemExit(f'Missing private environment variable: {name}')
    if not args.keystore.is_file():
        raise SystemExit('Recovered keystore is unavailable; do not generate a replacement.')
    result = subprocess.run([
        'keytool', '-list', '-v', '-keystore', str(args.keystore),
        '-storepass:env', 'RELEASE_STORE_PASSWORD', '-alias', record['alias'],
    ], capture_output=True, text=True)
    match = re.search(r'SHA256:\s*([0-9A-Fa-f:]+)', result.stdout)
    if result.returncode or not match:
        raise SystemExit('Keystore verification failed. No secrets were changed.')
    if match.group(1).replace(':', '').lower() != record['signer_sha256']:
        raise SystemExit('Certificate differs from the permanent identity. No secrets were changed.')
    names = record['ci_secret_names']
    values = [base64.b64encode(args.keystore.read_bytes()).decode(),
              os.environ['RELEASE_STORE_PASSWORD'], record['alias'],
              os.environ['RELEASE_KEY_PASSWORD']]
    if len(names) != 4:
        raise SystemExit('Incomplete public secret-name mapping.')
    for name, value in zip(names, values):
        result = subprocess.run(['gh', 'secret', 'set', name, '--repo', 'Tomex777/Build'],
                                input=value, capture_output=True, text=True)
        if result.returncode:
            raise SystemExit(f'Could not configure {name}; earlier entries may have been updated. '
                             'Use an owner-authenticated gh CLI with Actions-secret permission.')
        print(f'Configured {name}')
    print('Identity preserved. Final signed-binary API 26/API 36 acceptance is still required.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, subprocess.SubprocessError):
        sys.exit('Required owner tooling is unavailable. No private material was printed.')
