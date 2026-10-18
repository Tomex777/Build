#!/usr/bin/env python3
"""Extract two geographically anchored landing DEM patches from NASA data.

Moon: NASA SVS CGI Moon Kit, LRO/LOLA ldem_16_uint.tif
      16 pixels/degree, elevation (unsigned 16-bit - 20000) * 0.5 meters.
Mars: NASA MGS/MOLA MEGDR, megt90n000eb.img
      16 pixels/degree, signed big-endian 16-bit elevation in meters.
Output: 161x161 big-endian signed heights / 1000 render units.
        1 render unit = 3 kilometers for both height and local horizontal axes.
Tycho crater (-43.31, 348.64E) and Olympus Mons (18.65, 226.2E).
Input DEMs have limited spatial resolution; we do NOT reconstruct subpixel
geology or pretend to provide rover-scale photographic terrain.
"""
import argparse
import math
import mmap
import struct
from pathlib import Path

SIDE = 161
STEP_KM = 0.17 * 3.0
MOON_R_KM = 1737.4
MARS_R_KM = 3389.5


def bilinear(px, py, width, height, getter):
    x0 = int(math.floor(px)) % width
    x1 = (x0 + 1) % width
    y0 = max(0, min(height - 1, int(math.floor(py))))
    y1 = min(height - 1, y0 + 1)
    fx, fy = px - math.floor(px), py - math.floor(py)
    return ((1 - fx) * (1 - fy) * getter(x0, y0)
            + fx * (1 - fy) * getter(x1, y0)
            + (1 - fx) * fy * getter(x0, y1)
            + fx * fy * getter(x1, y1))


def sample_patch(lat0, lon0, radius_km, width, height, lon_offset, get):
    def elev_m(xi, zi):
        dx_km = (xi - (SIDE - 1) / 2) * STEP_KM
        dz_km = (zi - (SIDE - 1) / 2) * STEP_KM
        lat = lat0 + dz_km / (math.pi * radius_km / 180)
        lon = (lon0 + dx_km / (math.pi * radius_km / 180 * math.cos(math.radians(lat)))) % 360
        # Moon TIFF spans -180..+180; Mars PDS spans 0..360 east.
        px = ((lon + lon_offset) % 360) * (width / 360.0)
        py = (90.0 - lat) * (height / 180.0)
        return bilinear(px, py, width, height, get)

    center_height = sample_patch_center = elev_m(SIDE // 2, SIDE // 2)
    output = bytearray()
    minimum = float("inf")
    maximum = float("-inf")
    for zi in range(SIDE):
        for xi in range(SIDE):
            height_m = elev_m(xi, zi)
            minimum = min(minimum, height_m)
            maximum = max(maximum, height_m)
            # 1000 quantization steps per render unit, 3000 meters per unit.
            render_milliunits = round((height_m - center_height) / 3.0)
            if not -32768 <= render_milliunits <= 32767:
                raise ValueError("DEM elevation delta exceeds signed tile range")
            output.extend(struct.pack(">h", render_milliunits))
    assert len(output) == SIDE * SIDE * 2
    return output, (minimum, maximum, center_height)


def generate_moon(tif_path, dest):
    from PIL import Image
    with Image.open(tif_path) as im:
        im.load()
        assert im.size == (5760, 2880), f"Unexpected LOLA dimensions: {im.size}"
        get = lambda x, y: (int(im.getpixel((x, y))) - 20000) / 2.0
        # TIFF longitude origin is 180W, hence +180 modulo 360.
        data, stats = sample_patch(-43.31, 348.64, MOON_R_KM, 5760, 2880, 180.0, get)
    dest.write_bytes(data)
    return stats


def generate_mars(img_path, dest):
    with img_path.open("rb") as fp:
        assert img_path.stat().st_size == 5760 * 2880 * 2, "Unexpected MOLA data size"
        with mmap.mmap(fp.fileno(), 0, access=mmap.ACCESS_READ) as data:
            get = lambda x, y: struct.unpack_from(">h", data, 2 * (y * 5760 + x))[0]
            result, stats = sample_patch(18.65, 226.2, MARS_R_KM, 5760, 2880, 0.0, get)
    dest.write_bytes(result)
    return stats


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--moon", type=Path, required=True)
    parser.add_argument("--mars", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    moon_stats = generate_moon(args.moon, args.out / "moon_lola_terrain.bin")
    mars_stats = generate_mars(args.mars, args.out / "mars_mola_terrain.bin")
    print("LOLA Tycho height range (meters):", moon_stats)
    print("MOLA Olympus Mons height range (meters):", mars_stats)


if __name__ == "__main__":
    main()
