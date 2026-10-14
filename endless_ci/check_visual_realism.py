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


def check_lunar_stippling():
    """Reject the known grazing-slope normal-map regression at the fixed
    lunar runtime-test camera. This is an artifact check, not a realism score.
    The reviewed broken Android frame has ~6% high-curvature pixels here;
    terrain/rock edges in the corrected shader remain well below that.
    """
    img = open_frame("moon-surface-render").convert("L")
    crop = img.crop((int(img.width * .15), int(img.height * .34),
                     int(img.width * .95), int(img.height * .80)))
    px = crop.load()
    severe = total = 0
    for y in range(1, crop.height - 1):
        for x in range(1, crop.width - 1):
            laplacian = abs(4 * px[x, y] - px[x-1, y] - px[x+1, y]
                            - px[x, y-1] - px[x, y+1])
            severe += laplacian > 12
            total += 1
    fraction = severe / max(1, total)
    print(f"Moon grazing-slope high-curvature pixel fraction: {fraction:.4f}")
    assert fraction < .045, (
        "Moon slope has excessive high-frequency stippling; inspect normal "
        "amplitude/world units and compare the actual Android screenshot"
    )


check_lunar_stippling()


def check_composed_landing(name):
    """The HUD and SurfaceView must agree in the actual composed window.
    A correct PixelCopy alone previously hid a mixed orbital landing frame.
    Sample the central unobstructed scene, outside branding and controls.
    """
    rendered = open_frame(name + "-render")
    composed = open_frame(name)
    assert rendered.size == composed.size, "Landing capture coordinate frames differ"
    a, b = rendered.load(), composed.load()
    total = samples = 0
    for y in range(int(rendered.height * .40), int(rendered.height * .70), 4):
        for x in range(int(rendered.width * .20), int(rendered.width * .80), 4):
            total += sum(abs(a[x, y][c] - b[x, y][c]) for c in range(3))
            samples += 3
    difference = total / max(1, samples) / 255.0
    print(f"{name}: composed/surface scene difference {difference:.4f}")
    assert difference < .035, (
        f"{name}: composed landing disagrees with rendered terrain; inspect "
        "frame-atomic navigation and compositor/capture synchronization"
    )


check_composed_landing("moon-surface")
check_composed_landing("mars-surface")
print("Android planetary landing visibility checks passed")
