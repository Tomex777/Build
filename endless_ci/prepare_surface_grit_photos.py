#!/usr/bin/env python3
"""Small, optional, seam-free micro-albedo from public NASA mission photographs.

NOT georegistration: Opportunity's Endeavour soil is NOT Olympus Mons, and
Apollo 11 Tranquility soil is NOT Tycho. The photographs provide reference
grain statistics only, never site elevation, rover artifacts or illumination.
The spacecraft DEM and registered NASA Trek mosaic remain authoritative.
"""
import argparse
from io import BytesIO
from pathlib import Path
from urllib.request import Request, urlopen
from PIL import Image, ImageChops, ImageFilter, ImageOps

SIDE = 256
MARS_URL = "https://d2pn8kiwq2w21t.cloudfront.net/original_images/jpegPIA14533.jpg"
MOON_URL = "https://commons.wikimedia.org/wiki/Special:FilePath/AS11-40-5876%20%2821039138933%29.jpg?width=1024"

def texture_from_photo(image: Image.Image) -> Image.Image:
    # Exclude photo borders and colour/film processing; retain only grain.
    src = ImageOps.exif_transpose(image).convert("L")
    w, h = src.size
    side = int(min(w, h) * 0.68)
    patch = src.crop(((w-side)//2, (h-side)//2, (w+side)//2, (h+side)//2))
    patch = patch.resize((SIDE//2, SIDE//2), Image.Resampling.LANCZOS)
    # Remove historic Sun/shadow from the source photo: do not double-light it.
    mean = patch.filter(ImageFilter.GaussianBlur(radius=11))
    detail = ImageChops.subtract(patch, mean, scale=1.65, offset=128)
    # Mirror four quadrants for exactly seamless small repeating atlas.
    result = Image.new("L", (SIDE, SIDE))
    result.paste(detail, (0, 0))
    result.paste(ImageOps.mirror(detail), (SIDE//2, 0))
    result.paste(ImageOps.flip(detail), (0, SIDE//2))
    result.paste(ImageOps.flip(ImageOps.mirror(detail)), (SIDE//2, SIDE//2))
    return result

def get_bytes(url: str) -> bytes:
    request = Request(url, headers={
        "User-Agent": "Endless/1.0 NASA educational visualization (github.com/Tomex777/Build)",
        "Accept": "image/*"
    })
    with urlopen(request, timeout=25) as response:
        data = response.read(12_000_001)
    if len(data) > 12_000_000 or len(data) < 4000:
        raise ValueError(f"invalid mission photograph size: {len(data)}")
    return data

def fetch_photo(url: str) -> Image.Image:
    with Image.open(BytesIO(get_bytes(url))) as im:
        im.load()
        if min(im.size) < 500:
            raise ValueError("mission photograph is too low resolution")
        return im.copy()

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for body, url in (("mars", MARS_URL), ("moon", MOON_URL)):
        file = args.out / (body + "_surface_grit.webp")
        try:
            photo = fetch_photo(url)
            texture_from_photo(photo).save(file, "WEBP", quality=87, method=6)
            print(f"{body}: NASA reference grain included: {file.stat().st_size} bytes")
        except Exception as exc:
            file.unlink(missing_ok=True)
            print(f"::warning::{body} source photo unavailable ({exc}); procedural regolith retained")

if __name__ == "__main__":
    main()
