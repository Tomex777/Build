"""Pure fixture tests: received multipart uploads must match exact file bytes."""
import hashlib
import unittest

from wave2_fixture import UPLOAD_BYTES, uploaded_part_sha256


class UploadDigestTests(unittest.TestCase):
    def test_actual_file_bytes_hashed_not_entire_multipart(self):
        boundary = "night-qa-boundary"
        body = (
            b"--night-qa-boundary\r\n"
            b'Content-Disposition: form-data; name="qa-file"; filename="night-qa-upload.txt"\r\n'
            b"Content-Type: text/plain\r\n\r\n"
            + UPLOAD_BYTES +
            b"\r\n--night-qa-boundary--\r\n"
        )
        self.assertEqual(
            uploaded_part_sha256(body, 'multipart/form-data; boundary="night-qa-boundary"'),
            hashlib.sha256(UPLOAD_BYTES).hexdigest()
        )
        self.assertNotEqual(
            hashlib.sha256(body).hexdigest(), hashlib.sha256(UPLOAD_BYTES).hexdigest()
        )

    def test_absent_boundary_or_wrong_field_never_verifies(self):
        body = (
            b"--night-test\r\n"
            b'Content-Disposition: form-data; name="other"; filename="file.txt"\r\n\r\n'
            + UPLOAD_BYTES + b"\r\n--night-test--\r\n"
        )
        self.assertIsNone(uploaded_part_sha256(body, "text/plain"))
        self.assertIsNone(uploaded_part_sha256(body, "multipart/form-data; boundary=night-test"))

    def test_missing_closing_crlf_does_not_hash_partial_bytes(self):
        body = (b"--night-test\r\n"
                b'Content-Disposition: form-data; name="qa-file"; filename="file.txt"\r\n\r\n'
                + UPLOAD_BYTES)
        self.assertIsNone(uploaded_part_sha256(body, "multipart/form-data; boundary=night-test"))


if __name__ == "__main__":
    unittest.main()
