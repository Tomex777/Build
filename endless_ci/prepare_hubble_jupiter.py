#!/usr/bin/env python3
"""Validate and downsample NASA SVS 12021 Hubble WFC3 Jupiter cloud maps."""
import argparse
from pathlib import Path
from PIL import Image, ImageOps

SOURCE_SIZE = (3600, 1800)
OUTPUT_SIZE = (3072, 1536)


def convert(source: Path, output: Path) -> None:
    with Image.open(source) as photograph:
        if photograph.size != SOURCE_SIZE:
            raise ValueError(f"Expected NASA Jupiter 3600x1800; got {photograph.size}")
        if photograph.format not in ("TIFF", "JPEG", "PNG"):
            raise ValueError(f"Unsupported Hubble image format: {photograph.format}")
        photograph.load()
        # Preserve both hemispheres, poles, longitude order and true 2:1 map.
        image = ImageOps.exif_transpose(photograph).convert("RGB").resize(
            OUTPUT_SIZE, Image.Resampling.LANCZOS
        )
        if any(hi - lo < 12 for lo, hi in image.getextrema()):
            raise ValueError("Hubble map appears blank or colour-corrupt")
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = output.with_suffix(".temporary.jpg")
        try:
            image.save(temporary, "JPEG", quality=91, subsampling=0,
                       optimize=True, progressive=True)
            with Image.open(temporary) as packaged:
                if packaged.size != OUTPUT_SIZE or packaged.format != "JPEG":
                    raise ValueError("Invalid packaged Jupiter map")
                packaged.verify()
            temporary.replace(output)
        finally:
            temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    convert(args.source, args.output)
    print(f"NASA Jupiter verified: {args.output.stat().st_size} bytes")


if __name__ == "__main__":
    main()
