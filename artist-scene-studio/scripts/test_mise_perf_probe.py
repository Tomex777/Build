"""Offline parser tests; real Android evidence is gathered by mise_perf_probe.py."""
import unittest

from mise_perf_probe import parse_total_pss_kib, parse_ui_frames_ms, percentile, summarize


class ProbeTests(unittest.TestCase):
    def test_modern_memory_summary(self):
        self.assertEqual(parse_total_pss_kib("TOTAL PSS: 24,567    TOTAL RSS: 40,000"), 24567)

    def test_api26_memory_table(self):
        self.assertEqual(parse_total_pss_kib("Native Heap   17 5\n TOTAL  12345  678 2"), 12345)

    def test_missing_memory_is_not_invented(self):
        self.assertIsNone(parse_total_pss_kib("No process found"))

    def test_framestats_excludes_incomplete_and_bad_rows(self):
        sample = """---PROFILEDATA---
Flags,IntendedVsync,FrameCompleted
0,1000000000,1010000000
1,1000000000,1090000000
0,2000000000,2040000000
0,3000000000,0
0,abc,def
---PROFILEDATA---
"""
        self.assertEqual(parse_ui_frames_ms(sample), [10.0, 40.0])
        summary = summarize("TOTAL PSS: 1024", sample)
        self.assertEqual(summary["uiFrameSamples"], 2)
        self.assertEqual(summary["uiFramesOver16_67Ms"], 1)
        self.assertEqual(summary["uiCompletionMsP95"], 40.0)

    def test_without_ui_samples_is_not_mislabeled_fps(self):
        summary = summarize("unknown", "Profile data inapplicable")
        self.assertEqual(summary["uiFrameSamples"], 0)
        self.assertIsNone(summary["uiCompletionMsP50"])
        self.assertIn("not Filament GPU", summary["caveat"])

    def test_percentile(self):
        self.assertEqual(percentile([1, 5, 10, 20], 0.95), 20)
        self.assertEqual(percentile([1, 5, 10, 20], 0.50), 5)
        self.assertIsNone(percentile([], 0.95))


if __name__ == "__main__":
    unittest.main()
