#!/usr/bin/env python3
"""Check that two reader screenshots captured at different GIF frames differ."""

import sys
from PIL import Image, ImageChops, ImageStat

if len(sys.argv) != 3:
    raise SystemExit("usage: assert_gif_animation.py <first.png> <second.png>")


def center(path: str) -> Image.Image:
    image = Image.open(path).convert("RGB")
    width, height = image.size
    return image.crop((width * 45 // 100, height * 45 // 100,
                       width * 55 // 100, height * 55 // 100))


first, second = map(center, sys.argv[1:3])
mean_difference = sum(ImageStat.Stat(ImageChops.difference(first, second)).mean) / 3
if mean_difference < 25:
    raise SystemExit(f"GIF frame screenshots did not change enough (mean RGB delta {mean_difference:.1f})")
print(f"Animated GIF frame change verified (mean RGB delta {mean_difference:.1f})")
