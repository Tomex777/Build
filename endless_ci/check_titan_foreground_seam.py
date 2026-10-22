#!/usr/bin/env python3
"""Gate for one-pixel orange pinholes at Titan's foreground/midfield join.

The real Oct 20 API36 2400x1080 screenshot had ~253 isolated bright
orange pixels in a diagonal line where ground should be continuous.
Check actual UIAutomator screenshot pixels, NOT source code or mocks.
Uses Pillow only, already provisioned in the Android workflow.
"""
from pathlib import Path
from PIL import Image
import sys


def count_warm_pinholes(path: Path) -> int:
    with Image.open(path) as image:
        rgb = image.convert("RGB")
        width, height = rgb.size
        assert width >= 960 and height >= 600, "Titan screenshot is too small"
        pixels = rgb.load()
        count = 0
        # Exclude the joystick, Take off button and the hard sky horizon.
        for y in range(round(height * .607), round(height * .857)):
            for x in range(round(width * .18), round(width * .82)):
                red, green, _ = pixels[x, y]
                above = pixels[x, y-3]
                below = pixels[x, y+3]
                if (2*red - above[0] - below[0] > 28 and
                    2*green - above[1] - below[1] > 8):
                    count += 1
        return count


def main() -> None:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "artifacts/endless-m5-runtime/screenshots"
    )
    shot = root / "extended-titan.png"
    assert shot.is_file(), f"Missing actual Titan screenshot: {shot}"
    pinholes = count_warm_pinholes(shot)
    print(f"Titan near-field isolated orange pinhole pixels: {pinholes}")
    assert pinholes < 60, (
        f"{pinholes} bright orange pixels cross Titan's terrain join. "
        "Inspect the real screenshot; never release with sky leaking through ground."
    )


if __name__ == "__main__":
    main()
