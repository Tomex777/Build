"""Test that evidence contact sheets never silently invent absent worlds."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from PIL import Image
from build_visual_contact_sheet import assemble, CAPTURES


class VisualContactSheetTest(unittest.TestCase):
    def test_all_eight_actual_screens_are_required(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            for _, name in CAPTURES:
                Image.new("RGB", (640, 360), (61, 87, 93)).save(root / name)
            result = root / "sheet.png"
            assemble(root, result, tile_width=128)
            with Image.open(result) as image:
                self.assertEqual((310, 530), image.size)
            (root / "extended-titan.png").unlink()
            with self.assertRaisesRegex(FileNotFoundError, "extended-titan.png"):
                assemble(root, result, tile_width=128)

    def test_rejects_too_small_screenshot(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            for _, name in CAPTURES:
                Image.new("RGB", (639, 359)).save(root / name)
            with self.assertRaisesRegex(ValueError, "full-resolution"):
                assemble(root, root / "sheet.png")


if __name__ == "__main__":
    unittest.main()
