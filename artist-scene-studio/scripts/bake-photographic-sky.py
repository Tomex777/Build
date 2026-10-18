#!/usr/bin/env python3
"""Bake a CC0 *photographic* Poly Haven equirectangular sky into Filament cubemap faces.

Original sky pixels belong to the credited Poly Haven photographer (CC0).
No Magic Poser code, textures, binary packages or models are reused.

Source: https://polyhaven.com/a/kloofendal_48d_partly_cloudy_puresky
Source image: HDRIs/extra/Tonemapped JPG/kloofendal_48d_partly_cloudy_puresky.jpg
Face ordering and orientation match createOutdoorSky.kt: +X -X +Y -Y +Z -Z.
"""
import argparse
import hashlib
import math
import struct
import zlib
from pathlib import Path

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--source", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--size", type=int, default=512)
    args = p.parse_args()
    from PIL import Image

    image = Image.open(args.source).convert("RGB")
    image.thumbnail((4096, 2048), Image.Resampling.LANCZOS)
    width, height = image.size
    source = image.load()
    size = args.size
    result = bytearray(size * size * 4 * 6)
    pi = math.pi
    for face in range(6):
        for y in range(size):
            v = 2 * (y + .5) / size - 1
            for x in range(size):
                u = 2 * (x + .5) / size - 1
                if face == 0: dx,dy,dz = 1,-v,-u
                elif face == 1: dx,dy,dz = -1,-v,u
                elif face == 2: dx,dy,dz = u,1,v
                elif face == 3: dx,dy,dz = u,-1,-v
                elif face == 4: dx,dy,dz = u,-v,1
                else: dx,dy,dz = -u,-v,-1
                mag = math.sqrt(dx*dx+dy*dy+dz*dz)
                dx,dy,dz = dx/mag,dy/mag,dz/mag
                longitude = math.atan2(dz,dx)
                latitude = math.acos(max(-1.,min(1.,dy)))
                ix = int((longitude/(2*pi)+.5)*width) % width
                iy = min(height-1,int(latitude/pi*height))
                r,g,b = source[ix,iy]
                offset = ((face*size+y)*size+x)*4
                result[offset:offset+4] = bytes((r,g,b,255))
    # Little-endian magic+size, then zlib-compressed RGBA cube.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(b"MISESKY1" + struct.pack("<I",size) + zlib.compress(result,6))
    print("CC0 Poly Haven photographed sky:", image.size, "face=",size,
          "source SHA256=",hashlib.sha256(args.source.read_bytes()).hexdigest(),
          "cubemap bytes=",args.output.stat().st_size)

if __name__ == "__main__":
    main()
