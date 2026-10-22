"""Regression checks for photograph structure in *real-screen* gate.

The fixtures here are synthetic negative/positive controls only. Production
quality uses raw full-resolution Android emulator screenshots, not these images.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import subprocess
import sys
import tempfile
import unittest

CHECKER = Path(__file__).with_name("check_gas_giant_visuals.py")


class GasGiantScreenshotQualityTest(unittest.TestCase):
    def make_screens(self, root: Path, with_bands: bool):
        for planet in ("jupiter", "saturn"):
            screen = Image.new("RGB", (800, 400), (0, 0, 0))
            draw = ImageDraw.Draw(screen)
            # An untextured lit globe has substantial brightness/contrast;
            # the original screenshot gate accidentally accepted it.
            for r in range(96, 0, -1):
                brightness = int(82 + 104 * (1 - r / 96))
                draw.ellipse(
                    (400 - r, 200 - r, 400 + r, 200 + r),
                    fill=(brightness,) * 3,
                )
            if with_bands:
                # Deliberate high-spatial-frequency positive control;
                # this is not a planet texture or an Android screenshot.
                for y in range(226, 280, 7):
                    draw.line((429, y, 470, y), fill=(210, 128, 110), width=3)
            screen.save(root / f"orbital-{planet}-oblate.png")
            (root / f"orbital-{planet}-centre.txt").write_text(
                "400 200 800 400", encoding="utf-8"
            )

    def check(self, root: Path) -> subprocess.CompletedProcess:
        return subprocess.run(
            [sys.executable, str(CHECKER), str(root)],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            check=False,
        )

    def test_gradient_only_sphere_fails_despite_good_mean_brightness(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.make_screens(root, with_bands=False)
            result = self.check(root)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("lacks photographic cloud structure", result.stderr)
            self.assertIn("brightness=0.", result.stdout)

    def test_locally_detailed_disk_passes_structure_gate(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.make_screens(root, with_bands=True)
            result = self.check(root)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("passed visibility checks", result.stdout)


if __name__ == "__main__":
    unittest.main()
