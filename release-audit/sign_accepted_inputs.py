#!/usr/bin/env python3
"""Sign audited unsigned CI inputs with an existing, fingerprint-pinned owner key.

This produces signing candidates, never a production-finalized status. Test the
exact outputs on API 26 and API 36 before distribution.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def execute(command, label):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'{label} failed; output is withheld to protect signing credentials.')
    return result.stdout


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    root = Path(__file__).parent
    records = json.loads((root / 'release-identities.json').read_text())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', choices=records, required=True)
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    args = parser.parse_args()
    record = records[args.app]
    if record.get('candidate_source_head', record['head']) != record['head']:
        raise ValueError('Branch moved since these inputs were accepted. Re-audit current CI before signing.')
    for name in ('RELEASE_STORE_PASSWORD', 'RELEASE_KEY_PASSWORD'):
        if not os.environ.get(name):
            raise ValueError(f'Missing private environment variable: {name}')
    certificate = execute(['keytool', '-list', '-v', '-keystore', str(args.keystore),
                           '-storepass:env', 'RELEASE_STORE_PASSWORD', '-alias', record['alias']],
                          'Owner certificate verification')
    fingerprint = re.search(r'SHA256:\s*([0-9A-Fa-f:]+)', certificate)
    if not fingerprint or fingerprint.group(1).replace(':', '').lower() != record['signer_sha256']:
        raise ValueError('Owner certificate does not match the permanent identity. Refusing rotation.')
    args.output.mkdir(parents=True, exist_ok=False)
    signing = ['-keystore', str(args.keystore), '-storepass:env', 'RELEASE_STORE_PASSWORD',
               '-keypass:env', 'RELEASE_KEY_PASSWORD']
    jarsigner = ['jarsigner'] if shutil.which('jarsigner') else [
        'java', '-m', 'jdk.jartool/sun.security.tools.jarsigner.Main']
    outputs = []
    for name, expected_hash in record['unsigned_inputs'].items():
        source = args.inputs / name
        if not source.is_file() or sha256(source) != expected_hash:
            raise ValueError(f'Unsigned input is missing or differs from audited CI: {name}')
        stem = source.stem.replace('-unsigned', '').replace('_PRODUCTION_RAW', '')
        destination = args.output / (stem + '-owner-key-candidate' + source.suffix)
        if source.suffix == '.apk':
            tool = str(args.build_tools / 'apksigner')
            # Refuse already signed and malformed-signature inputs alike. The
            # manifest hashes select only the original unsigned CI binaries.
            verified = subprocess.run([tool, 'verify', str(source)], capture_output=True)
            if verified.returncode == 0:
                raise ValueError(f'Refusing to replace an existing APK signing identity: {name}')
            execute([tool, 'sign', '--ks', str(args.keystore), '--ks-key-alias', record['alias'],
                     '--ks-pass', 'env:RELEASE_STORE_PASSWORD', '--key-pass', 'env:RELEASE_KEY_PASSWORD',
                     '--min-sdk-version', '26', '--out', str(destination), str(source)], 'APK signing')
            verification = execute([tool, 'verify', '--verbose', '--print-certs', str(destination)],
                                   'Independent Android signature verification')
            actual = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', verification)
            if not actual or actual.group(1) != record['signer_sha256']:
                raise ValueError('Final APK signer differs from the permanent identity.')
            badging = execute([str(args.build_tools / 'aapt'), 'dump', 'badging', str(destination)],
                              'Final APK metadata verification')
            expected = (f"package: name='{record['application_id']}' "
                        f"versionCode='{record['version_code']}' versionName='{record['version_name']}'")
            if expected not in badging or "sdkVersion:'26'" not in badging or "targetSdkVersion:'36'" not in badging:
                raise ValueError('Final package/version/SDK metadata differs from the release identity.')
            manifest = execute([str(args.build_tools / 'aapt'), 'dump', 'xmltree',
                                str(destination), 'AndroidManifest.xml'], 'Release manifest verification')
            if re.search(r'android:(debuggable|testOnly).*0xffffffff', manifest):
                raise ValueError('Final artifact is debuggable or test-only.')
            (args.output / (destination.name + '.badging.txt')).write_text(badging)
        else:
            execute(jarsigner + signing + ['-signedjar', str(destination), str(source), record['alias']],
                    'AAB signing')
            verification = execute(jarsigner + ['-verify', str(destination)], 'AAB verification')
            if 'jar verified.' not in verification:
                raise ValueError('Final AAB has no verified JAR signature.')
            bundle_cert = execute(['keytool', '-printcert', '-jarfile', str(destination)],
                                  'AAB certificate verification')
            actual = re.search(r'SHA256:\s*([0-9A-Fa-f:]+)', bundle_cert)
            if not actual or actual.group(1).replace(':', '').lower() != record['signer_sha256']:
                raise ValueError('Final AAB signer differs from the APK identity.')
        (args.output / (destination.name + '.signature.txt')).write_text(verification)
        outputs.append({'file': destination.name, 'sha256': sha256(destination),
                        'signer_sha256': record['signer_sha256'], 'install_test': 'NOT RUN'})
    (args.output / 'SHA256SUMS.txt').write_text(''.join(
        f"{row['sha256']}  {row['file']}\n" for row in outputs))
    (args.output / 'SIGNING-RECORD.json').write_text(json.dumps(
        dict(record, outputs=outputs, final_result='SIGNED CANDIDATE; FINAL ACCEPTANCE REQUIRED'), indent=2))
    print(f'Signature-verified candidates prepared for {args.app}. Install acceptance has NOT run.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        sys.exit(str(error))
