#!/usr/bin/env python3
"""Verify Mise APKs were signed using the pinned permanent production certificate."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


def normalize_fingerprint(value: str) -> str:
    """Accept raw or colon-separated SHA-256 fingerprints, reject other input."""
    if not re.fullmatch(r"[0-9a-fA-F]{64}|(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}", value.strip()):
        raise ValueError("Expected a 64-digit SHA-256 certificate fingerprint")
    return value.strip().replace(":", "").lower()


def signer_fingerprint(verification_output: str) -> str:
    """Require exactly one signer in the output of apksigner verify --print-certs."""
    digests = re.findall(
        r"(?m)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$",
        verification_output,
    )
    if len(digests) != 1:
        raise ValueError(f"Expected exactly one verified APK signer, found {len(digests)}")
    return normalize_fingerprint(digests[0])


def assert_expected_signer(verification_output: str, expected: str) -> None:
    actual = signer_fingerprint(verification_output)
    if actual != normalize_fingerprint(expected):
        raise ValueError(
            f"Production signing certificate mismatch: expected {normalize_fingerprint(expected)}, got {actual}"
        )


def verify_apks(identity_path: Path, apk_paths: list[Path], apksigner: str) -> None:
    identity = json.loads(identity_path.read_text(encoding="utf-8"))
    expected = normalize_fingerprint(identity["signerCertificateSHA256"])
    for apk_path in apk_paths:
        if not apk_path.is_file() or not apk_path.stat().st_size:
            raise ValueError(f"APK is absent or empty: {apk_path}")
        result = subprocess.run(
            [apksigner, "verify", "--verbose", "--print-certs", str(apk_path)],
            capture_output=True,
            text=True,
            check=True,
        )
        assert_expected_signer(result.stdout, expected)
        print(f"Verified Mise permanent signing identity: {apk_path.name} [{expected}]")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--identity", type=Path, required=True)
    parser.add_argument("--apksigner", default="apksigner")
    parser.add_argument("apks", nargs="+", type=Path)
    args = parser.parse_args()
    try:
        verify_apks(args.identity, args.apks, args.apksigner)
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as exc:
        print(f"Production signature verification failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
