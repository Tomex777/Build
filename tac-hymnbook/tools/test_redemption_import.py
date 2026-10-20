"""Import approval rules; tests use synthetic text, never scraped hymn lyrics."""
import unittest
from redemption_import import RedemptionImportError, validate_batch


def sample():
    return {
        "schema_version": 1, "collection": "main",
        "hymns": [{
            "number": 350,
            "title_yo": "Ìyìn ti Oluwa",
            "title_en": "Praise the Lord",
            "verses_yo": [["Ìyìn ti Oluwa", "Mo yọ̀"]],
            "verses_en": [["Praise the Lord", "I rejoice"]],
            "source_pages": [123],
            "source_url": None,
            "reviewer": "TAC publisher-approved editorial check",
            "rights_verified": True,
            "printed_edition_verified": True,
            "permission_reference": "Licence confirmation ref. editorial-test",
        }],
    }


class RedemptionLicensedImportTests(unittest.TestCase):
    def test_approved_batch_is_normalized_and_section_safe(self):
        result = validate_batch(sample())
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["number"], 350)
        self.assertEqual(result[0]["collection"], "main")
        self.assertEqual(result[0]["text_status"], "licensed_bilingual_print_verified")
        self.assertTrue(result[0]["rights_verified"])
        self.assertEqual(result[0]["verses_yo"][0][0], "Ìyìn ti Oluwa")

    def test_no_rights_means_no_release(self):
        data = sample()
        data["hymns"][0]["rights_verified"] = False
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)

    def test_print_not_checked_means_no_release(self):
        data = sample()
        data["hymns"][0]["printed_edition_verified"] = False
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)

    def test_no_yoruba_or_english_is_rejected(self):
        for field in ("verses_yo", "verses_en"):
            data = sample()
            data["hymns"][0][field] = []
            with self.assertRaises(RedemptionImportError):
                validate_batch(data)

    def test_no_invented_edition_or_source(self):
        data = sample()
        data["hymns"][0]["source_pages"] = []
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)
        data = sample()
        data["hymns"][0]["permission_reference"] = " "
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)

    def test_akokun_number_cannot_leak_into_redemption(self):
        data = sample()
        data["collection"] = "akokun"
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)

    def test_existing_draft_must_be_explicitly_replaced(self):
        data = sample()
        with self.assertRaises(RedemptionImportError):
            validate_batch(data, known_numbers={350})
        data["hymns"][0]["replaces_working_draft"] = True
        result = validate_batch(data, known_numbers={350})
        self.assertTrue(result[0]["replaces_working_draft"])

    def test_duplicate_and_out_of_range_number(self):
        data = sample()
        data["hymns"].append(dict(data["hymns"][0]))
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)
        data = sample()
        data["hymns"][0]["number"] = 801
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)

    def test_verses_must_preserve_unicode_and_be_one_line_per_array_item(self):
        data = sample()
        data["hymns"][0]["verses_yo"][0][0] = "Ii\u0301yi\u0300n"
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)
        data = sample()
        data["hymns"][0]["verses_en"][0][0] = "One\nTwo"
        with self.assertRaises(RedemptionImportError):
            validate_batch(data)


if __name__ == "__main__":
    unittest.main()
