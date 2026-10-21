#!/usr/bin/env python3
"""Regression gates over real Android screenshots for four new landing worlds.

This detects washed-out ice, missing horizons, black/flat regolith and an
Earth-blue sky on Titan. It is NOT an assertion of photorealistic quality.
A human must still inspect the full-resolution Android evidence artifact.
"""
from pathlib import Path
from PIL import Image
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "artifacts/endless-m5-runtime/screenshots"
)


def load(body):
    file = root / f"extended-{body}.png"
    assert file.is_file(), f"Missing genuine device screenshot: {file}"
    with Image.open(file) as im:
        image = im.convert("RGB")
    assert image.width >= 640 and image.height >= 360
    return image


def samples(image, left, top, right, bottom):
    width, height = image.size
    crop = image.crop((
        int(width*left), int(height*top),
        int(width*right), int(height*bottom)
    ))
    pixels = crop.load()
    colors = []
    for y in range(0, crop.height, 5):
        for x in range(0, crop.width, 5):
            colors.append(tuple(channel / 255 for channel in pixels[x, y]))
    means = tuple(sum(p[i] for p in colors) / len(colors) for i in range(3))
    light = sum(means) / 3
    return means, light


for world in ("mercury", "ceres", "europa", "titan"):
    frame = load(world)
    zenith, sky = samples(frame, .38, .11, .61, .31)
    terrain, ground = samples(frame, .36, .82, .63, .93)
    print(f"{world}: sky={sky:.3f} RGB={zenith!r}; "
          f"ground={ground:.3f} RGB={terrain!r}")
    assert ground > .09, f"{world}: black or absent ground at eye level"
    assert abs(sky - ground) > .065, f"{world}: horizon invisible"
    if world == "titan":
        assert zenith[0] > zenith[1] > zenith[2], (
            "Titan haze lost its calibrated orange-brown wavelength response"
        )
        assert .06 < sky < .60, "Titan haze clipping or missing"
    elif world == "europa":
        assert sky < .12, "Europa cannot have an Earth-like daylight sky"
        assert .30 < ground < .87, "Europa ice exposure crushed or bleached"
    else:
        assert sky < .12, f"{world}: airless world should have a black sky"
        assert ground < .60, f"{world}: airless regolith blown out"
print("All four observed Android landing scenes passed visibility checks")
