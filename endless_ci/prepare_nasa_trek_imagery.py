#!/usr/bin/env python3
"""Build georeferenced NASA Trek lunar/Martian orbital photo mosaics.

NASA WMTS documentation:
https://trek.nasa.gov/tiles/apidoc/trekAPI.html?body=moon
https://trek.nasa.gov/tiles/apidoc/trekAPI.html?body=mars

MOLA/LOLA measured elevation patches span 81.6km on each axis
(center: Tycho -43.31, 348.64E / Olympus 18.65, 226.2E).
We raster the corresponding photographic area into square JPEG assets.
The source photos are orbital-resolution and MUST NOT be called rover imagery.
"""
import argparse
import io
import math
import sys
import urllib.request
from pathlib import Path
from PIL import Image

SITE = {
    "moon": {
        "lat": -43.31, "lon": -11.36, "radius": 1737.4,
        "zoom": 8, "id": "Moon/EQ/LRO_WAC_Mosaic_Global_303ppd_v02",
    },
    "mars": {
        "lat": 18.65, "lon": -133.8, "radius": 3389.5,
        "zoom": 7, "id": "Mars/EQ/Mars_Viking_MDIM21_ClrMosaic_global_232m",
    },
}
HALF_EXTENT_KM = (161 - 1) / 2.0 * 0.17 * 3.0
OUTPUT_PIXELS = 1024


def geographic_point(site, east_km, north_km):
    lat = site["lat"] + math.degrees(north_km / site["radius"])
    lon = site["lon"] + math.degrees(
        east_km / (site["radius"] * math.cos(math.radians(lat)))
    )
    return lat, ((lon + 180) % 360) - 180


def wmts_pixel(zoom, lon_deg, lat_deg):
    """WMTS uses a 2:1 globe: 2^(z+1) columns x 2^z rows, 256px/tile."""
    factor = (2**zoom * 256) / 180.0
    return (lon_deg + 180.0) * factor, (90.0 - lat_deg) * factor


def required_tile_indices(site):
    z = site["zoom"]
    corners = [
        wmts_pixel(z, *reversed(geographic_point(site, x, y)))
        for x in [-HALF_EXTENT_KM, HALF_EXTENT_KM]
        for y in [-HALF_EXTENT_KM, HALF_EXTENT_KM]
    ]
    minx, maxx = min(c[0] for c in corners), max(c[0] for c in corners)
    miny, maxy = min(c[1] for c in corners), max(c[1] for c in corners)
    return (
        range(math.floor(minx/256), math.floor(maxx/256)+1),
        range(max(0, math.floor(miny/256)), min(2**z-1, math.floor(maxy/256))+1),
    )


def fetch_tile(site, row, col):
    z = site["zoom"]
    url = (f"https://trek.nasa.gov/tiles/{site['id']}/"
           f"1.0.0/default/default028mm/{z}/{row}/{col % (2**(z+1))}.jpg")
    req = urllib.request.Request(url, headers={"User-Agent": "Endless-NASA-Educational/1.0"})
    with urllib.request.urlopen(req, timeout=25) as r:
        data = r.read()
    im = Image.open(io.BytesIO(data))
    im.load()
    if im.size != (256, 256):
        raise ValueError(f"Unexpected NASA tile size: {im.size} at {url}")
    return im.convert("RGB")


def mosaic(site):
    columns, rows = required_tile_indices(site)
    first_x, first_y = min(columns), min(rows)
    canvas = Image.new("RGB", (len(columns)*256, len(rows)*256))
    successes = 0
    errors = []
    for y in rows:
        for x in columns:
            try:
                im = fetch_tile(site, y, x)
            except Exception as e:
                errors.append(f"tile {x},{y}: {e}")
                continue
            canvas.paste(im, ((x-first_x)*256, (y-first_y)*256))
            successes += 1
    required = len(columns)*len(rows)
    # Any uncovered pixel would misrepresent planetary imagery. Abort and
    # retain the procedural surface rather than publish partially fake photos.
    if successes != required:
        raise RuntimeError(f"Only fetched {successes}/{required} NASA tiles: {errors}")
    return canvas, first_x, first_y


def reproject(site):
    canvas, first_x, first_y = mosaic(site)
    z = site["zoom"]
    # Every output pixel is mapped from a geographical location, not stretched
    # arbitrarily to fill the terrain. 1 pixel ~=80 meters on target 81.6 km.
    output = Image.new("RGB", (OUTPUT_PIXELS, OUTPUT_PIXELS))
    pixel = output.load()
    src = canvas.load()
    width, height = canvas.size
    for y in range(OUTPUT_PIXELS):
        north = HALF_EXTENT_KM * (1.0 - 2.0 * (y+0.5)/OUTPUT_PIXELS)
        for x in range(OUTPUT_PIXELS):
            east = HALF_EXTENT_KM * (2.0 * (x+0.5)/OUTPUT_PIXELS - 1.0)
            lat, lon = geographic_point(site, east, north)
            px, py = wmts_pixel(z, lon, lat)
            sx = min(width-1, max(0, round(px - first_x*256)))
            sy = min(height-1, max(0, round(py - first_y*256)))
            pixel[x,y] = src[sx,sy]
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--worlds", nargs="+", choices=SITE, default=list(SITE))
    args = parser.parse_args()
    args.out.mkdir(exist_ok=True, parents=True)
    for name in args.worlds:
        im = reproject(SITE[name])
        target = args.out / f"{name}_trek_landing.jpg"
        im.save(target, quality=93, optimize=True, subsampling=0)
        if target.stat().st_size < 60000:
            raise ValueError(f"NASA tile output is suspiciously small: {target}")
        print(f"{name}: NASA Trek site mosaic ({im.width}x{im.height}), {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
