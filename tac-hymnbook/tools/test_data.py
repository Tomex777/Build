"""Fast, offline consistency checks for photographed hymn catalogues."""
import json
import unittest
from pathlib import Path

from build_asset import ROOT, compile_asset


class HymnDataTest(unittest.TestCase):
    def test_catalogue_is_unique_and_contiguous(self):
        hymns = compile_asset()["hymns"]
        self.assertEqual([h["number"] for h in hymns], list(range(1, 12)))
        self.assertEqual(len(hymns), 11)

    def test_all_photo_hymns_have_lyrics_in_both_languages(self):
        hymns = {h["number"]: h for h in compile_asset()["hymns"]}
        self.assertEqual({n for n, h in hymns.items() if h["verses_yo"]}, set(range(1, 12)))
        self.assertEqual({n for n, h in hymns.items() if h["verses_en"]}, set(range(1, 12)))
        for h in hymns.values():
            if not h["verses_yo"]:
                self.assertEqual(h["text_status"], "missing")
            else:
                self.assertTrue("draft" in h["text_status"] or "partial" in h["text_status"])
                self.assertTrue(h["source_pages"])

    def test_verse_counts_and_partial_hymn(self):
        hymns = compile_asset()["hymns"]
        for hymn in hymns:
            self.assertEqual(len(hymn["verses_yo"]), len(hymn["verses_en"]))
            self.assertGreater(len(hymn["verses_yo"]), 0)
            self.assertTrue(hymn["source_pages"])
            self.assertEqual(hymn["source_pages"], sorted(hymn["source_pages"]))
            for verse in hymn["verses_yo"] + hymn["verses_en"]:
                for line in verse:
                    self.assertNotIn(r"\\n", line)
        self.assertEqual(hymns[-1]["text_status"], "partial_from_photo")
        self.assertEqual(hymns[-1]["source_pages"], [4])

    def test_assets_are_committed_and_up_to_date(self):
        output = ROOT / "android/app/src/main/assets/hymns.json"
        recorded = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(recorded, compile_asset())

    def test_yoruba_is_stored_as_unicode(self):
        hymns = compile_asset()["hymns"]
        self.assertIn("Ọ", hymns[0]["title_yo"])
        self.assertIn("à", hymns[3]["title_yo"])
        self.assertEqual(hymns[3]["verses_yo"][0][0], "Níwájú ìtẹ́ àánú")


if __name__ == "__main__":
    unittest.main()
