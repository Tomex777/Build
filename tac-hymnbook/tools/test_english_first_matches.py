"""Keep research-only Yoruba cross-hymnal matches separate from publishable lyrics.

These records identify English first-line correspondences only. They do not
authorize copying another publisher's Yoruba text into the app.
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "data/redemption-hymnal-catalogue.json"
MATCHES = ROOT / "data/research/english-first-yoruba-matches.json"


class EnglishFirstMatchesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = json.loads(INDEX.read_text(encoding="utf-8"))
        cls.matches = json.loads(MATCHES.read_text(encoding="utf-8"))

    def test_match_numbers_and_english_titles_agree_with_redemption(self):
        reference = {h["number"]: h for h in self.catalogue["entries"]}
        seen = set()
        for match in self.matches["records"]:
            n = match["redemption_number"]
            self.assertTrue(type(n) is int and 1 <= n <= 800)
            self.assertNotIn(n, seen, f"Repeated Redemption number {n}")
            seen.add(n)
            self.assertEqual(
                match["english_first_line"], reference[n]["title_en"],
                f"English-first mismatch at Redemption #{n}",
            )
            self.assertFalse(reference[n].get("title_yo"),
                             f"Research duplicate of known Yoruba title #{n}")
        self.assertTrue(seen, "Research set cannot be empty")

    def test_candidates_cannot_be_mistaken_for_publishable_lyrics(self):
        for match in self.matches["records"]:
            self.assertEqual(
                match["status"], "cross_hymnal_candidate_not_TAC_print_verified"
            )
            self.assertIs(match["needs_tac_print_comparison"], True)
            self.assertIs(match["distribution_rights_verified"], False)
            self.assertIs(match["lyrics_copied"], False)
            self.assertTrue(match["source_url"].startswith("https://"))
            self.assertTrue(match["english_index_url"].startswith("https://"))
            self.assertTrue(match["yoruba_first_line_as_source"].strip())
            self.assertNotIn("verses_yo", match)
            self.assertNotIn("verses_en", match)

    def test_separate_numbering_is_explicit(self):
        for match in self.matches["records"]:
            n = match["source_hymn_number"]
            self.assertTrue(n is None or type(n) is int and n > 0)


if __name__ == "__main__":
    unittest.main()
