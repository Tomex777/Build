"""Fast, offline consistency checks for photographed hymn catalogues."""
import json
import unittest
from pathlib import Path

from build_asset import ROOT, compile_asset


class HymnDataTest(unittest.TestCase):
    def test_catalogue_is_unique_and_contiguous(self):
        hymns = compile_asset()["hymns"]
        self.assertEqual([h["number"] for h in hymns], [*range(1, 18), 50, 178, 190, 200])
        self.assertEqual(len(hymns), 21)

    def test_all_photo_hymns_have_lyrics_in_both_languages(self):
        hymns = {h["number"]: h for h in compile_asset()["hymns"]}
        self.assertEqual({n for n, h in hymns.items() if h["verses_yo"]}, set([*range(1, 18), 50]))
        self.assertEqual(
            {n for n, h in hymns.items() if h["verses_en"]},
            set([*range(1, 18), 50, 178, 190, 200])
        )
        for h in hymns.values():
            if not h["verses_yo"]:
                self.assertIn(h["number"], {178, 190, 200})
                self.assertEqual(h["text_status"], "public_domain_english_alternate_needs_edition_review")
                self.assertEqual(h["source_pages"], [])
            else:
                self.assertTrue("draft" in h["text_status"] or "partial" in h["text_status"] or "online" in h["text_status"])
                self.assertTrue(h["source_pages"])

    def test_verse_counts_and_partial_hymn(self):
        hymns = compile_asset()["hymns"]
        for hymn in hymns:
            if hymn["number"] not in {178, 190, 200}:
                self.assertEqual(len(hymn["verses_yo"]), len(hymn["verses_en"]))
                self.assertGreater(len(hymn["verses_yo"]), 0)
                self.assertTrue(hymn["source_pages"])
            else:
                self.assertEqual(len(hymn["verses_en"]), 5)
                self.assertFalse(hymn["verses_yo"])
            self.assertEqual(hymn["source_pages"], sorted(hymn["source_pages"]))
            for verse in hymn["verses_yo"] + hymn["verses_en"]:
                for line in verse:
                    self.assertNotIn(r"\\n", line)
        self.assertEqual(hymns[10]["source_pages"], [4, 5])
        self.assertEqual(hymns[16]["source_pages"], [6])
        self.assertEqual(hymns[16]["number"], 17)
        self.assertEqual(hymns[17]["number"], 50)
        self.assertEqual(hymns[-1]["number"], 200)

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
        self.assertEqual(sum(e["english_title_status"] == "verified_from_RH2006_index" for e in entries), 208)
        for number in (178, 190, 200):
            self.assertEqual(entries[number - 1]["english_title_status"], "verified_from_RH2006_index")
        self.assertEqual(entries[176]["title_en"].lower(), "jesus was slain for me")
        self.assertLess(sum(e["title_en"] is not None for e in entries), 800)

    def test_provenance_of_english_alternative_texts(self):
        path = ROOT / "data/english-public-domain.json"
        entries = json.loads(path.read_text(encoding="utf-8"))["hymns"]
        self.assertEqual([h["number"] for h in entries], [178, 190, 200])
        for hymn in entries:
            self.assertEqual(hymn["rights"], "public_domain_historical_english_text")
            self.assertEqual(hymn["text_status"], "public_domain_english_alternate_needs_edition_review")
            self.assertTrue(hymn["source_url"].startswith("https://hymnary.org/"))
            self.assertEqual(len(hymn["verses_en"]), 5)
            self.assertFalse("verses_yo" in hymn)

    def test_akokun_is_a_distinct_addendum(self):
        source = ROOT / "data/akokun-collection.json"
        asset = ROOT / "android/app/src/main/assets/akokun_index.json"
        catalog = json.loads(source.read_text(encoding="utf-8"))
        self.assertEqual(json.loads(asset.read_text(encoding="utf-8")), catalog)
        self.assertEqual(catalog["id"], "akokun")
        self.assertEqual(catalog["category"], "supplement")
        self.assertEqual(catalog["numbering_scheme"], "independent_from_redemption")
        self.assertEqual(catalog["last_number"], 305)
        self.assertEqual(catalog["hymn_count"], 305)
        self.assertEqual(catalog["source_pages"], list(range(459, 468)))
        self.assertEqual([h["number"] for h in catalog["hymns"]], list(range(1, 306)))
        self.assertEqual(catalog["photographed_index_rows"], 302)
        self.assertEqual(catalog["numbered_hymns_with_title"], 299)
        self.assertEqual(catalog["missing_title_numbers"], [128, 188, 211, 219, 272, 281])
        self.assertEqual(catalog["shared_number_with_multiple_index_lines"], [138, 218, 288])
        self.assertEqual(catalog["hymns"][304]["number"], 305)
        self.assertIsNotNone(catalog["hymns"][304]["title_yo"])
        self.assertTrue(all(h["verses_yo"] is None and h["verses_en"] is None for h in catalog["hymns"]))

    def test_akokun_index_photographs_agree_with_merged_catalogue(self):
        merged = json.loads((ROOT / "data/akokun-collection.json").read_text(encoding="utf-8"))
        raw_entries = []
        for p in range(459, 468):
            path = ROOT / f"data/akokun-index-page-{p}.json"
            page = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(page["printed_index_page"], p)
            raw_entries.extend(page["entries"])
        self.assertEqual(len(raw_entries), 302)
        for entry in raw_entries:
            number = entry["number"]
            self.assertGreaterEqual(number, 1)
            self.assertLessEqual(number, 305)
            h = merged["hymns"][number - 1]
            self.assertIn(entry["first_line_yo"], [x["text"] for x in h["opening_lines"]])
        self.assertEqual(
            sum(len(h["opening_lines"]) for h in merged["hymns"]), len(raw_entries)
        )

    def test_source_matching_queue_does_not_publish_unchecked_lyrics(self):
        path = ROOT / "data/akokun-source-matches.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        candidates = data["candidates"]
        self.assertEqual(len(candidates), 9)
        self.assertEqual(
            [x["number"] for x in candidates],
            [1, 37, 45, 47, 65, 101, 104, 130, 303]
        )
        catalogue = json.loads(
            (ROOT / "data/akokun-collection.json").read_text(encoding="utf-8")
        )
        for candidate in candidates:
            number = candidate["number"]
            self.assertEqual(candidate["collection"], "akokun")
            self.assertTrue(catalogue["hymns"][number - 1]["title_yo"])
            self.assertTrue(candidate["source_url"].startswith("https://"))
            self.assertEqual(candidate["full_text_status"], "not_imported")
            self.assertEqual(candidate["yoruba_translation_rights"], "not_yet_cleared")
            self.assertEqual(candidate["tac_stanza_check"], "not_yet_checked")
        self.assertEqual(catalogue["numbered_hymns_with_title"], 299)
        self.assertEqual(sum(bool(h.get("title_en")) for h in catalogue["hymns"]), 8)
        for number in (1, 45, 47, 65, 101, 104, 130, 303):
            self.assertTrue(catalogue["hymns"][number - 1].get("title_en"))
        self.assertIsNone(catalogue["hymns"][36].get("title_en"))

    def test_yoruba_is_stored_as_unicode(self):
        hymns = compile_asset()["hymns"]
        self.assertIn("Ọ", hymns[0]["title_yo"])
        self.assertIn("à", hymns[3]["title_yo"])
        self.assertEqual(hymns[3]["verses_yo"][0][0], "Níwájú ìtẹ́ àánú")


if __name__ == "__main__":
    unittest.main()
