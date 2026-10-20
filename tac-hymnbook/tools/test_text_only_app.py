"""Acceptance guard: TAC Hymns is an offline text reader, not an audio player."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "android/app/src/main"


class TextOnlyAppTests(unittest.TestCase):
    def test_no_audio_dependencies_or_network_permissions(self):
        manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("android.permission.INTERNET", manifest)
        sources = "\n".join(
            p.read_text(encoding="utf-8")
            for p in (APP / "java/com/night/tachymns").glob("*.kt")
        )
        for forbidden in (
            "MediaPlayer", "HymnAudioPlayer", "HymnAudioControls",
            "OfflineRecordingsScreen", "LearnHymnsHome", "youtube.com",
            "onDownloadAudio", "onImportAudio",
        ):
            self.assertNotIn(forbidden, sources, f"Removed audio feature reappeared: {forbidden}")

    def test_no_bundled_or_streamed_audio(self):
        assets = APP / "assets"
        self.assertFalse((assets / "audio_catalog.json").exists())
        self.assertFalse((assets / "bundled_audio_catalog.json").exists())
        self.assertFalse((assets / "audio").exists())
        self.assertFalse((APP / "java/com/night/tachymns/HymnAudio.kt").exists())
        self.assertFalse((APP / "java/com/night/tachymns/HymnAudioUi.kt").exists())

    def test_reader_keeps_yoruba_and_english_texts(self):
        code = (APP / "java/com/night/tachymns/MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn('LanguageTab("Yorùbá"', code)
        self.assertIn('LanguageTab("English"', code)
        self.assertIn('SearchInput(query, onQueryChange)', code)
        self.assertIn('Text("Text pending"', code)


if __name__ == "__main__":
    unittest.main()
