#!/usr/bin/env python3
"""Sign existing CI release binaries with Mise's durable private signing identity."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--config', required=True, type=Path)
parser.add_argument('--artifacts', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--apksigner', default='apksigner')
args = parser.parse_args()
config = json.loads(args.config.read_text())
keystore = args.config.parent / config['keystore']
assert keystore.is_file(), 'Permanent keystore is missing'
args.output.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['MISE_RELEASE_STORE_PASSWORD'] = config['storePassword']
env['MISE_RELEASE_KEY_PASSWORD'] = config['keyPassword']
common = ['--ks', str(keystore), '--ks-key-alias', config['alias'],
          '--ks-pass', 'env:MISE_RELEASE_STORE_PASSWORD', '--key-pass', 'env:MISE_RELEASE_KEY_PASSWORD']
certificates = set()
outputs = []
for abi in ('arm64', 'universal'):
    source = args.artifacts / f'Mise-1.0.0-{abi}-release-unsigned.apk'
    target = args.output / f'Mise-1.0.0-{abi}-production-signed.apk'
    assert source.is_file(), f'Missing CI release input: {source.name}'
    subprocess.run([args.apksigner, 'sign', *common, '--out', str(target), str(source)], env=env, check=True)
    proof = subprocess.run([args.apksigner, 'verify', '--verbose', '--print-certs', str(target)], capture_output=True, text=True, check=True).stdout
    certificate = re.search(r'Signer #1 certificate SHA-256 digest: (\S+)', proof)
    assert certificate, 'Verified certificate identity was absent'
    certificates.add(certificate.group(1))
    (args.output / f'{abi.upper()}-PRODUCTION-SIGNATURE.txt').write_text(proof)
    outputs.append(target)
assert len(certificates) == 1, 'APK signing identities differ'
source = args.artifacts / 'Mise-1.0.0-release-unsigned.aab'
target = args.output / 'Mise-1.0.0-production-signed.aab'
subprocess.run(['jarsigner', '-keystore', str(keystore), '-storepass:env', 'MISE_RELEASE_STORE_PASSWORD',
                '-keypass:env', 'MISE_RELEASE_KEY_PASSWORD', '-signedjar', str(target), str(source), config['alias']], env=env, check=True, capture_output=True)
proof = subprocess.run(['jarsigner', '-verify', '-verbose', '-certs', str(target)], capture_output=True, text=True, check=True).stdout
assert 'jar verified.' in proof, 'AAB verification failed'
(args.output / 'AAB-PRODUCTION-SIGNATURE.txt').write_text(proof)
outputs.append(target)
(args.output / 'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in outputs))
print('Permanent production APK/AAB signatures and SHA-256 hashes verified.')
