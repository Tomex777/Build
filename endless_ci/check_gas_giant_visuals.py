#!/usr/bin/env python3
"""Detect black, flat, empty or missing focused gas-giant Android captures.

This checks the actual full-screen emulator render AFTER the renderer
settles on each planet. Qualitative scientific image comparison is still
required; a pixel statistic is not photorealism certification.
"""
from pathlib import Path
from PIL import Image, ImageStat
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "artifacts/endless-m5-runtime/screenshots"
)


for planet in ("jupiter", "saturn"):
    path = root / f"orbital-{planet}-oblate.png"
    assert path.is_file(), f"Missing actual Android {planet} screenshot"
    coordinate_file = root / f"orbital-{planet}-centre.txt"
    assert coordinate_file.is_file(), f"Missing Android GL projection: {planet}"
    cx, cy, vw, vh = (float(item) for item in
                       coordinate_file.read_text().split())
    with Image.open(path) as screenshot:
        assert screenshot.width >= 640 and screenshot.height >= 360
        x, y = screenshot.size
        center_x, center_y = cx / vw * x, cy / vh * y
        assert x * .4 <= center_x <= x * .6, (
            f"{planet}: globe is not centered after the camera fly"
        )
        assert y * .4 <= center_y <= y * .6
        # Ignore the floating planet name at the center. Sample the two
        # adjacent cloud bands/ring spans, not HUD or unrelated Sun pixels.
        radius_x = int(x * .075)
        radius_y = int(y * .13)
        disk = screenshot.convert("RGB").crop((
            int(center_x)-radius_x, int(center_y)-radius_y,
            int(center_x)+radius_x, int(center_y)+radius_y
        ))
    stats = ImageStat.Stat(disk)
    mean = sum(stats.mean) / (3 * 255)
    contrast = sum(stats.stddev) / (3 * 255)
    print(f"{planet}: observed central brightness={mean:.3f}; "
          f"spatial contrast={contrast:.3f}")
    assert mean > .043, f"{planet}: planet appears black/missing"
    assert contrast > .006, f"{planet}: no visible cloud/ring structure"
print("Actual Android gas-giant orbital screenshots passed visibility checks")
