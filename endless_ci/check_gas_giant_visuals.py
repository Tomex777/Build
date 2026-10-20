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
    with Image.open(path) as screenshot:
        assert screenshot.width >= 640 and screenshot.height >= 360
        x, y = screenshot.size
        # Avoid top navigation / bottom action UI; centered planet takes
        # up a meaningful fraction of the 48-degree orbital camera.
        disk = screenshot.convert("RGB").crop((
            int(x * .43), int(y * .32),
            int(x * .57), int(y * .68)
        ))
    stats = ImageStat.Stat(disk)
    mean = sum(stats.mean) / (3 * 255)
    contrast = sum(stats.stddev) / (3 * 255)
    print(f"{planet}: observed central brightness={mean:.3f}; "
          f"spatial contrast={contrast:.3f}")
    assert mean > .043, f"{planet}: planet appears black/missing"
    assert contrast > .006, f"{planet}: no visible cloud/ring structure"
print("Actual Android gas-giant orbital screenshots passed visibility checks")
