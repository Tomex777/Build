"""Regression tests for the pinned Mise production APK signing identity."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "verify_signing_identity.py"
spec = importlib.util.spec_from_file_location("verify_signing_identity", SCRIPT)
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)
EXPECTED = "ed0659b38a48c90de2bee280db5eb9998ab1984c8f8434aea095e0107f85c85b"


def proof(digest=EXPECTED):
    return (
        "Verifies\n"
        "Verified using v2 scheme (APK Signature Scheme v2): true\n"
        f"Signer #1 certificate SHA-256 digest: {digest}\n"
    )


class SigningIdentityTests(unittest.TestCase):
    def test_accepts_exact_pinned_identity(self):
        signing.assert_expected_signer(proof(), EXPECTED)

    def test_accepts_colon_separated_certificate_format(self):
        separated = ":".join(EXPECTED[i:i + 2] for i in range(0, 64, 2))
        signing.assert_expected_signer(proof(separated), EXPECTED)

    def test_rejects_wrong_key(self):
        with self.assertRaisesRegex(ValueError, "mismatch"):
            signing.assert_expected_signer(proof("0" * 64), EXPECTED)

    def test_rejects_missing_certificate(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            signing.assert_expected_signer("Verifies\n", EXPECTED)

    def test_rejects_multiple_signers(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            signing.assert_expected_signer(proof() + "Signer #2 certificate SHA-256 digest: " + EXPECTED + "\n", EXPECTED)

    def test_rejects_malformed_sha256(self):
        with self.assertRaises(ValueError):
            signing.normalize_fingerprint("this is not a fingerprint")

    def test_checks_actual_signed_apks_with_apksigner(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            identity = root / "identity.json"
            apk = root / "Mise.apk"
            identity.write_text(json.dumps({"signerCertificateSHA256": EXPECTED}))
            apk.write_bytes(b"fake non-empty fixture")
            with patch.object(signing.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, proof(), "")) as run:
                signing.verify_apks(identity, [apk], "apksigner")
            run.assert_called_once_with(
                ["apksigner", "verify", "--verbose", "--print-certs", str(apk)],
                capture_output=True, text=True, check=True,
            )

    def test_rejects_empty_release_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            identity = root / "identity.json"
            apk = root / "Mise.apk"
            identity.write_text(json.dumps({"signerCertificateSHA256": EXPECTED}))
            apk.write_bytes(b"")
            with self.assertRaisesRegex(ValueError, "absent or empty"):
                signing.verify_apks(identity, [apk], "apksigner")


if __name__ == "__main__":
    unittest.main()
