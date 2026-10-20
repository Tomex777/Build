"""Regression checks for native NIGHT UI <-> Android ADB acceptance contracts.

These tests never launch an emulator and never count an unverified feature as PASS.
"""
import unittest
from unittest.mock import patch
from xml.etree import ElementTree

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

    def test_browser_chrome_is_not_tapped_through_visible_keyboard(self):
        qa = Device.__new__(Device)
        node = ElementTree.Element("node", {"text": "Tabs", "bounds": "[10,800][100,900]"})
        qa.find = lambda *args, **kwargs: node
        calls = []
        def shell(*args, **kwargs):
            calls.append(args)
            return "mInputShown=true" if args[:2] == ("dumpsys", "input_method") else ""
        with patch("wave2_blackbox.shell", side_effect=shell), patch("wave2_blackbox.time.sleep"):
            qa.click("Tabs")
        self.assertLess(calls.index(("input", "keyevent", "4")),
                        calls.index(("input", "tap", "55", "850")))

    def test_hidden_keyboard_never_causes_surprise_back_navigation(self):
        qa = Device.__new__(Device)
        qa.find = lambda *args, **kwargs: ElementTree.Element(
            "node", {"text": "Home", "bounds": "[0,0][100,100]"})
        calls = []
        def shell(*args, **kwargs):
            calls.append(args)
            return "mShowRequested=true; mInputShown=false"
        with patch("wave2_blackbox.shell", side_effect=shell), patch("wave2_blackbox.time.sleep"):
            qa.click("Home")
        self.assertNotIn(("input", "keyevent", "4"), calls)
        self.assertIn(("input", "tap", "50", "50"), calls)

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
