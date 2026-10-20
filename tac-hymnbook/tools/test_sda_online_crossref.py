"""Check online SDA->Redemption matching without confusing another hymnal with TAC."""
from __future__ import annotations

import json
import re
import unittest
import unicodedata
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]


def normalized(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    replace = {"ye": "you", "thee": "you", "thou": "you",
               "thy": "your", "thine": "yours"}
    text = re.sub(r"\b(ye|thee|thou|thy|thine)\b",
                  lambda m: replace[m.group()], text)
    text = text.replace("’", "").replace("‘", "").replace("'", "")
    return " ".join(re.findall(r"[a-z0-9]+", text))


class SDAOnlineSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = json.loads((BASE / "data/redemption-hymnal-catalogue.json")
                               .read_text(encoding="utf-8"))
        cls.crossref = json.loads((BASE / "data/research/sda-open-yoruba-crossref.json")
                                  .read_text(encoding="utf-8"))

    def test_all_matched_english_titles_exist_at_redemption_numbers(self):
        bynum = {entry["number"]: entry for entry in cls_index(self.index)}
        for record in self.crossref["matches"]:
            n = record["redemption_number"]
            self.assertTrue(1 <= n <= 800)
            self.assertIn(n, bynum)
            self.assertEqual(record["redemption_english_first_line"],
                             bynum[n]["title_en"])
            self.assertEqual(normalized(record["redemption_english_first_line"]),
                             normalized(record["sda_english_title"]))
            self.assertEqual(record["redemption_existing_yoruba_first_line"],
                             bynum[n].get("title_yo"))
            self.assertTrue(record["sda_yoruba_title"].strip())

    def test_provenance_not_confused_with_finished_TAC_text(self):
        self.assertEqual(self.crossref["sda_catalogue_size"], 621)
        self.assertEqual(self.crossref["redemption_catalogue_size"], 800)
        records = self.crossref["matches"]
        self.assertEqual(len(records),
                         self.crossref["exact_crossref_record_count"])
        self.assertEqual(
            len(set(r["redemption_number"] for r in records)),
            self.crossref["redemption_unique_matched_hymns"]
        )
        for r in records:
            self.assertEqual(r["matching_method"], "normalized exact English title match")
            self.assertEqual(r["review_status"],
                             "same_english_original_only_TAC_Yoruba_wording_not_verified")
            self.assertIs(r["full_text_imported_into_TAC_app"], False)
            self.assertIs(r["source_lyrics_in_this_file"], False)
            self.assertTrue(r["sda_link"].endswith(str(r["sda_hymn_number"])))
            self.assertNotIn("lyrics", r)
            self.assertNotIn("verses_yo", r)


def cls_index(payload):
    return payload["entries"]


if __name__ == "__main__":
    unittest.main()
