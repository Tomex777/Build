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

    def test_only_photo_backed_drafts_have_lyrics(self):
        hymns = {h["number"]: h for h in compile_asset()["hymns"]}
        self.assertEqual({n for n, h in hymns.items() if h["verses_yo"]}, {4, 7})
        for h in hymns.values():
            if not h["verses_yo"]:
                self.assertEqual(h["text_status"], "missing")
            else:
                self.assertIn("draft", h["text_status"])
                self.assertTrue(h["source_pages"])

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
