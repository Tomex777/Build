"""Pure-Python Wave 2 fixture and evidence-contract unit tests (no adb needed)."""
import json
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer

from wave2_fixture import Handler, State, BIN, PNG, PDF
from wave2_blackbox import Device, Blocked


class FixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        Handler.state = State()
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.base = "http://127.0.0.1:" + str(cls.server.server_port)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def fetch(self, path, headers=None):
        req = Request(self.base + path, headers=headers or {})
        with urlopen(req, timeout=3) as resp:
            return resp.read(), resp

    def test_real_html_branch(self):
        result, resp = self.fetch("/links")
        self.assertIn(b'href="/branch/a"', result)
        self.assertIn(b"NIGHT QA LINKS", result)
        self.assertEqual(resp.status, 200)

    def test_redirect_tracks_destination(self):
        data, resp = self.fetch("/redirect")
        self.assertIn(b"REDIRECT LANDING", data)
        self.assertEqual(resp.geturl(), self.base + "/landing")

    def test_cookie_absent_then_present(self):
        self.fetch("/cookie/echo")
        self.fetch("/cookie/echo", {"Cookie": "nightQa=fixture"})
        stats = Handler.state.snapshot()
        self.assertFalse(stats["cookie_seen"][1])
        self.assertTrue(stats["cookie_seen"][2])
        # Only boolean observations, never cookie content.
        self.assertNotIn("nightQa", json.dumps(stats))

    def test_binary_mime_and_length(self):
        for path, expected, mime in (("/file.png", PNG, "image/png"),
                                      ("/file.pdf", PDF, "application/pdf"),
                                      ("/file.bin", BIN, "application/octet-stream")):
            data, response = self.fetch(path)
            self.assertEqual(data, expected)
            self.assertEqual(response.headers["Content-Type"], mime)
            self.assertIn("attachment", response.headers["Content-Disposition"])
        self.assertTrue(PDF.startswith(b"%PDF"))
        self.assertTrue(PNG.startswith(b"\x89PNG"))

    def test_limited_upload_post_without_persisting_bytes(self):
        content = b"TEST-ONLY-FIXTURE" * 100
        req = Request(self.base + "/upload/submit", data=content, method="POST")
        with urlopen(req, timeout=3) as resp:
            self.assertEqual(resp.status, 200)
        self.assertEqual(Handler.state.snapshot()["upload_length"], len(content))
        self.assertNotIn("TEST-ONLY", json.dumps(Handler.state.snapshot()))

    def test_authenticated_file_requires_test_cookie(self):
        with self.assertRaises(HTTPError) as failure:
            self.fetch("/secure/file.bin")
        self.assertEqual(failure.exception.code, 403)
        with self.assertRaises(HTTPError) as fake:
            self.fetch("/secure/file.bin", {"Cookie": "othernightQa=fixture"})
        self.assertEqual(fake.exception.code, 403)
        data, response = self.fetch(
            "/secure/file.bin", {"Cookie": "nightQa=fixture"})
        self.assertEqual(response.status, 200)
        self.assertEqual(data, BIN)
        stats = Handler.state.snapshot()
        self.assertGreaterEqual(stats["secure_download_denied"], 1)
        self.assertGreaterEqual(stats["secure_download_ok"], 1)
        self.assertNotIn("nightQa", json.dumps(stats))

    def test_not_found_retains_http_error(self):
        with self.assertRaises(HTTPError) as err:
            self.fetch("/not-found")
        self.assertEqual(err.exception.code, 404)

    def test_not_run_and_fail_are_not_false_pass(self):
        d = Device.__new__(Device)
        d.ready = set()
        d.rows = []
        d.out = None
        d.test("X01", "Blocked feature", lambda: (_ for _ in ()).throw(Blocked("unimplemented")))
        d.test("X02", "Broken feature", lambda: (_ for _ in ()).throw(AssertionError("bad")))
        d.test("X03", "Dependent feature", lambda: "should never execute",
               prerequisites=("X02",))
        self.assertEqual([r["status"] for r in d.rows], ["NOT_RUN", "FAIL", "BLOCKED"])


if __name__ == "__main__":
    unittest.main()
