#!/usr/bin/env python3
"""Simple automated perceptual sanity checks on real Android PixelCopy frames.

These assertions are NOT substitutes for visual review, scientific source
comparison or frame-time benchmarking. They catch two major regressions:
* Mars landing renders an inverted textured 'ceiling' instead of open sky.
* A badly clipped surface leaves nearly all ground/sky blank.
"""
from pathlib import Path
from PIL import Image
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "artifacts/endless-m5-runtime/screenshots"
)


def open_frame(name):
    path = root / (name + ".png")
    assert path.exists(), f"Missing Android PixelCopy evidence: {path}"
    im = Image.open(path).convert("RGB")
    assert im.width >= 640 and im.height >= 360, f"Bad screenshot size: {path}"
    return im


def chroma_and_variance(im, y_start, y_end):
    stride = max(2, im.width // 400)
    region = im.crop((im.width // 6, int(im.height * y_start),
                      im.width * 5 // 6, int(im.height * y_end)))
    px = region.load()
    total = n = 0
    mean = 0.0
    for y in range(0, region.height, 4):
        for x in range(0, region.width - stride, stride):
            a = px[x, y]
            b = px[x + stride, y]
            total += sum(abs(a[c] - b[c]) for c in range(3)) / (3.0 * 255.0)
            mean += sum(a) / (3.0 * 255.0)
            n += 1
    return total / max(1, n), mean / max(1, n)


def check(name, expect_sky):
    img = open_frame(name)
    top_detail, top_brightness = chroma_and_variance(img, 0.02, 0.10)
    lower_detail, lower_brightness = chroma_and_variance(img, 0.70, 0.95)
    print(f"{name}: sky detail {top_detail:.4f} avg {top_brightness:.3f}; "
          f"ground detail {lower_detail:.4f} avg {lower_brightness:.3f}")
    if expect_sky == "mars":
        # At 2.34 m camera height, open Martian sky must be present
        # near the top; the old 100%-ground ceiling had >0.040 detail
        # even over one adjacent screen pixel.
        assert top_detail < 0.045, (
            f"{name}: excess high-frequency detail at zenith; likely "
            "inverted terrain/backface or undersampled fragment noise"
        )
        assert top_brightness > 0.07, f"{name}: Mars sky unexpectedly black"
    else:
        assert top_brightness < 0.10, (
            f"{name}: Moon must have a black, airless sky"
        )
    assert lower_brightness > 0.025, f"{name}: terrain is black or missing"
    assert abs(top_brightness - lower_brightness) > 0.012, (
        f"{name}: no readable horizon/ground separation"
    )


check("mars-surface-render", "mars")
check("moon-surface-render", "moon")
print("Android planetary landing visibility checks passed")
