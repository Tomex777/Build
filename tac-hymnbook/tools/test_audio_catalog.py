"""Checks for the curated TAC recordings manifest; absence of tracks is not faked coverage."""
import json
from pathlib import Path
import re
import unittest
from urllib.parse import urlsplit

CATALOGUE = Path(__file__).resolve().parents[1] / "android/app/src/main/assets/audio_catalog.json"


class AudioCatalogueTests(unittest.TestCase):
    def test_approved_recordings_have_unambiguous_identity_and_rights(self):
        data = json.loads(CATALOGUE.read_text(encoding="utf-8"))
        self.assertEqual(data["schema_version"], 1)
        self.assertIsInstance(data["recordings"], list)
        keys = set()
        for row in data["recordings"]:
            section = row["collection"]
            self.assertIn(section, {"main", "akokun"})
            limit = 800 if section == "main" else 305
            self.assertIsInstance(row["number"], int)
            self.assertTrue(1 <= row["number"] <= limit)
            self.assertIn(row["language"], {"yo", "en"})
            self.assertIn(row["voice"], {"solo", "choir"})
            self.assertIs(row["rights_verified"], True)
            self.assertTrue(row["credit"].strip())
            self.assertTrue(row["rights"].strip())
            self.assertRegex(row["sha256"], r"^[a-fA-F0-9]{64}$")
            parts = urlsplit(row["url"])
            self.assertEqual(parts.scheme, "https")
            self.assertTrue(parts.netloc)
            self.assertFalse(parts.username or parts.password)
            key = tuple(row[k] for k in ("collection", "number", "language", "voice"))
            self.assertNotIn(key, keys)
            keys.add(key)

    def test_never_ship_placeholder_audio(self):
        for row in json.loads(CATALOGUE.read_text(encoding="utf-8"))["recordings"]:
            self.assertNotIn("example.com", row["url"])
            self.assertNotEqual(row["sha256"], "0" * 64)


if __name__ == "__main__":
    unittest.main()
