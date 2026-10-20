import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw
from prepare_hubble_jupiter import convert, OUTPUT_SIZE, SOURCE_SIZE


class HubblePreparationTest(unittest.TestCase):
    def test_observed_global_latitude_order_is_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            src = Path(folder) / "original.tif"
            dest = Path(folder) / "processed.jpg"
            source = Image.new("RGB", SOURCE_SIZE, (198, 130, 90))
            paint = ImageDraw.Draw(source)
            paint.rectangle((0, 0, 3599, 300), fill=(78, 104, 156))
            paint.rectangle((0, 1050, 3599, 1300), fill=(152, 72, 46))
            source.save(src)
            convert(src, dest)
            with Image.open(dest) as photograph:
                self.assertEqual(OUTPUT_SIZE, photograph.size)
                self.assertEqual("JPEG", photograph.format)
                self.assertGreater(photograph.getpixel((100, 100))[2],
                                   photograph.getpixel((100, 100))[0])
                self.assertGreater(photograph.getpixel((100, 1000))[0],
                                   photograph.getpixel((100, 1000))[2])

    def test_corrupt_dimensions_preserve_2k_fallback(self):
        with tempfile.TemporaryDirectory() as folder:
            src = Path(folder) / "tiny.png"
            dest = Path(folder) / "planet_jupiter.jpg"
            dest.write_bytes(b"original")
            Image.new("RGB", (1024, 512), (50, 50, 50)).save(src)
            with self.assertRaisesRegex(ValueError, "3600x1800"):
                convert(src, dest)
            self.assertEqual(b"original", dest.read_bytes())


if __name__ == "__main__":
    unittest.main()
