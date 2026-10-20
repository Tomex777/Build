"""Unit tests for the QA harness itself (not substitutes for Android emulator tests)."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from xml.etree import ElementTree

from acceptance import Acceptance


class HarnessUnitTests(unittest.TestCase):
    def test_filled_omnibar_is_replaced_without_placeholder_assumption(self):
        qa = Acceptance.__new__(Acceptance)
        field = ElementTree.Element("node", {
            "package": "com.night.browser",
            "class": "android.widget.EditText",
            "text": "about:blank",
            "bounds": "[60,1600][775,1700]",
        })
        calls = []

        def shell(*args, **kwargs):
            calls.append(args)
            if args[:2] == ("input", "text"):
                field.set("text", args[2])
            return ""

        qa.shell = shell
        qa.nodes = lambda: [field]
        with patch("acceptance.time.sleep"):
            qa.replace_omnibar("example.com")
        self.assertEqual(field.get("text"), "example.com")
        self.assertIn(("input", "keyevent", "123"), calls)
        self.assertIn(("input", "text", "example.com"), calls)

    def test_diagnostics_excludes_unrelated_android_system_crashes(self):
        with tempfile.TemporaryDirectory() as directory:
            qa = Acceptance.__new__(Acceptance)
            qa.output = Path(directory)
            qa.results = []
            qa.adb = lambda *args, **kwargs: (
                "10-20 E AndroidRuntime: FATAL EXCEPTION IN SYSTEM PROCESS: main\n"
                "10-20 E AndroidRuntime: java.lang.IllegalStateException: system error\n"
            )
            qa.diagnostics()
            self.assertEqual(qa.results[-1]["status"], "PASS")
            self.assertEqual((qa.output / "crashes.txt").read_text(), "\n")

    def test_diagnostics_detects_app_process_crash_and_redacts_other_logs(self):
        with tempfile.TemporaryDirectory() as directory:
            qa = Acceptance.__new__(Acceptance)
            qa.output = Path(directory)
            qa.results = []
            qa.adb = lambda *args, **kwargs: (
                "10-20 I Gecko: token=SECRET_TEST_ONLY https://private.example\n"
                "10-20 E AndroidRuntime: FATAL EXCEPTION: main\n"
                "10-20 E AndroidRuntime: Process: com.night.browser, PID: 4444\n"
                "10-20 E AndroidRuntime: java.lang.IllegalStateException: Only one GeckoRuntime instance is allowed\n"
                "10-20 E AndroidRuntime: at com.night.browser.BrowserController.<init>(BrowserController.kt:35)\n"
            )
            qa.diagnostics()
            self.assertEqual(qa.results[-1]["status"], "FAIL")
            diagnostic = (qa.output / "crashes.txt").read_text()
            self.assertIn("Only one GeckoRuntime instance", diagnostic)
            self.assertNotIn("SECRET_TEST_ONLY", diagnostic)
            self.assertNotIn("private.example", diagnostic)


if __name__ == "__main__":
    unittest.main()
