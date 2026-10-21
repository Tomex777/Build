import sys
import unittest
from pathlib import Path
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_surface_grit_photos import texture_from_photo, SIDE

class PhotoMicroMaterialTest(unittest.TestCase):
    def test_source_material_is_seamless_and_not_flat(self):
        src = Image.new("L", (800, 800), 110)
        d = ImageDraw.Draw(src)
        for n in range(250):
            x = (n * 97) % 790
            y = (n * 211) % 790
            d.ellipse((x, y, x + (n % 7) + 2, y + (n % 9) + 1), fill=60 + n % 110)
        tile = texture_from_photo(src)
        self.assertEqual(tile.size, (SIDE, SIDE))
        pixels = list(tile.tobytes())
        self.assertGreater(max(pixels) - min(pixels), 30)
        self.assertLess(max(abs(pixels[y*SIDE] - pixels[y*SIDE+SIDE-1]) for y in range(SIDE)), 1)
        self.assertLess(max(abs(pixels[x] - pixels[(SIDE-1)*SIDE+x]) for x in range(SIDE)), 1)

if __name__ == "__main__":
    unittest.main()
