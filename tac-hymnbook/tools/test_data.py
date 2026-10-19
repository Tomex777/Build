"""Fast, offline consistency checks for photographed hymn catalogues."""
import json
import unittest
from pathlib import Path

from build_asset import ROOT, compile_asset


class HymnDataTest(unittest.TestCase):
    def test_catalogue_is_unique_and_contiguous(self):
        hymns = compile_asset()["hymns"]
        self.assertEqual([h["number"] for h in hymns], [*range(1, 18), 50])
        self.assertEqual(len(hymns), 18)

    def test_all_photo_hymns_have_lyrics_in_both_languages(self):
        hymns = {h["number"]: h for h in compile_asset()["hymns"]}
        self.assertEqual({n for n, h in hymns.items() if h["verses_yo"]}, set([*range(1, 18), 50]))
        self.assertEqual({n for n, h in hymns.items() if h["verses_en"]}, set([*range(1, 18), 50]))
        for h in hymns.values():
            if not h["verses_yo"]:
                self.assertEqual(h["text_status"], "missing")
            else:
                self.assertTrue("draft" in h["text_status"] or "partial" in h["text_status"] or "online" in h["text_status"])
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
        self.assertEqual(hymns[10]["source_pages"], [4, 5])
        self.assertEqual(hymns[16]["source_pages"], [6])
        self.assertEqual(hymns[16]["number"], 17)
        self.assertEqual(hymns[-1]["number"], 50)

    def test_assets_are_committed_and_up_to_date(self):
        output = ROOT / "android/app/src/main/assets/hymns.json"
        recorded = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(recorded, compile_asset())

    def test_page_437_index_catalogue(self):
        index_file = ROOT / "data/index-page-437.json"
        entries = json.loads(index_file.read_text(encoding="utf-8"))["entries"]
        self.assertEqual(len(entries), 40)
        numbers = [entry["printed_number"] for entry in entries]
        self.assertEqual(len(numbers), len(set(numbers)))
        self.assertIn(50, numbers)
        self.assertIn(341, numbers)
        self.assertIn(424, numbers)
        self.assertIn(568, numbers)
        by_number = {e["printed_number"]: e for e in entries}
        self.assertIn("Rock of Ages", by_number[341]["redemption_english_title"])
        self.assertIn("Mercy of God", by_number[424]["redemption_english_title"])
        self.assertEqual(by_number[568]["redemption_english_title"], "To the Work")
        self.assertIn("Thine Arm", by_number[733]["redemption_english_title"])
        self.assertEqual({e["printed_number"] for e in entries if e["full_lyrics_imported"]}, {50})
        self.assertEqual(set(numbers).intersection(range(1, 18)), set())

    def test_redemption_catalogue_all_800_numbers(self):
        source = ROOT / "data/redemption-hymnal-catalogue.json"
        asset = ROOT / "android/app/src/main/assets/redemption_index.json"
        catalog = json.loads(source.read_text(encoding="utf-8"))
        self.assertEqual(json.loads(asset.read_text(encoding="utf-8")), catalog)
        entries = catalog["entries"]
        self.assertEqual(len(entries), 800)
        self.assertEqual([e["number"] for e in entries], list(range(1, 801)))
        self.assertEqual(entries[0]["title_en"].lower(), "lord of all being, throned afar")
        self.assertEqual(entries[49]["title_en"].lower(), "now thank we all our god")
        self.assertEqual(entries[733 - 1]["number"], 733)
        for entry in entries:
            self.assertIn(entry["english_title_status"], {
                "verified_from_RH2006_index",
                "candidate_from_photographed_index",
                "not_yet_matched",
            })
        self.assertEqual(sum(e["lyrics_imported"] for e in entries), 18)
        self.assertEqual(sum(e["english_title_status"] == "verified_from_RH2006_index" for e in entries), 177)
        self.assertEqual(entries[176]["title_en"].lower(), "jesus was slain for me")
        self.assertLess(sum(e["title_en"] is not None for e in entries), 800)

    def test_akokun_is_a_distinct_addendum(self):
        source = ROOT / "data/akokun-collection.json"
        asset = ROOT / "android/app/src/main/assets/akokun_index.json"
        catalog = json.loads(source.read_text(encoding="utf-8"))
        self.assertEqual(json.loads(asset.read_text(encoding="utf-8")), catalog)
        self.assertEqual(catalog["id"], "akokun")
        self.assertEqual(catalog["category"], "supplement")
        self.assertEqual(catalog["hymns"], [])
        self.assertIsNone(catalog["numbering_scheme"])

    def test_yoruba_is_stored_as_unicode(self):
        hymns = compile_asset()["hymns"]
        self.assertIn("Ọ", hymns[0]["title_yo"])
        self.assertIn("à", hymns[3]["title_yo"])
        self.assertEqual(hymns[3]["verses_yo"][0][0], "Níwájú ìtẹ́ àánú")


if __name__ == "__main__":
    unittest.main()
