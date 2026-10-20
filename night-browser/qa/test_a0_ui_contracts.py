"""Regression checks for native NIGHT UI <-> Android ADB acceptance contracts.

These tests never launch an emulator and never count an unverified feature as PASS.
"""
import unittest

from acceptance import Acceptance
from wave2_blackbox import Device


class NativeUiContracts(unittest.TestCase):
    def test_wave2_regular_count_ignores_other_numbers_and_keyboard(self):
        device = Device.__new__(Device)
        device.ui = lambda: [
            {"package": "com.night.browser", "text": "Tabs"},
            {"package": "com.night.browser", "text": "Regular  10"},
            {"package": "com.night.browser", "text": "Private  3"},
            {"package": "com.google.android.inputmethod.latin", "text": "9999"},
        ]
        self.assertEqual(device.regular_count(), 10)
        device.ui = lambda: [{"package": "com.night.browser", "text": "Private  3"}]
        with self.assertRaisesRegex(AssertionError, "Regular tab count"):
            device.regular_count()

    def test_independent_smoke_reads_regular_filter_counter(self):
        qa = Acceptance.__new__(Acceptance)
        qa.wait_for = lambda *args, **kwargs: None
        qa.nodes = lambda: [
            {"package": "com.night.browser", "text": "Regular  4"},
            {"package": "com.night.browser", "text": "Private  1"},
            {"package": "com.android.inputmethod.latin", "text": "88"},
        ]
        self.assertEqual(qa.count_tabs(), 4)

    def test_private_entry_uses_real_tabs_filter(self):
        qa = Acceptance.__new__(Acceptance)
        taps, waits = [], []
        qa.tap = lambda text, **kwargs: taps.append(text)
        qa.wait_for = lambda text, **kwargs: waits.append(text)
        qa.nodes = lambda: [
            {"package": "com.night.browser", "text": "Tabs"},
            {"package": "com.night.browser", "text": "Private  0"},
        ]
        qa.private_tabs()
        self.assertEqual(taps, ["Tabs", "Private  0", "New private tab"])
        self.assertIn("Private browsing", waits)


if __name__ == "__main__":
    unittest.main()
