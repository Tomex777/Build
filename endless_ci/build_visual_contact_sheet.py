#!/usr/bin/env python3
"""Produce an honest side-by-side sheet of *actual Android screenshots*.

No screenshots are synthesized, recolored or enhanced. No missing world is
silently replaced with artwork. The contact sheet is for visual review;
photorealistic/scientific quality must still be judged from originals.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import argparse

CAPTURES = (
    ("Mars", "mars-surface.png"),
    ("Moon", "moon-surface.png"),
    ("Mercury", "extended-mercury.png"),
    ("Ceres", "extended-ceres.png"),
    ("Europa", "extended-europa.png"),
    ("Titan", "extended-titan.png"),
    ("Jupiter", "orbital-jupiter-oblate.png"),
    ("Saturn", "orbital-saturn-oblate.png"),
)


def assemble(root: Path, output: Path, tile_width: int = 768) -> None:
    if tile_width < 128:
        raise ValueError("Contact sheet tiles need >=128px to preserve readability")
    tile_height = round(tile_width * 9 / 16)
    band = 40
    gap = 14
    margin = 20
    columns, rows = 2, (len(CAPTURES) + 1) // 2
    width = margin * 2 + columns * tile_width + (columns - 1) * gap
    height = margin * 2 + rows * (tile_height + band) + (rows - 1) * gap
    sheet = Image.new("RGB", (width, height), (12, 16, 22))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for i, (title, filename) in enumerate(CAPTURES):
        source = root / filename
        if not source.is_file():
            raise FileNotFoundError(f"Missing authentic Android frame: {source}")
        with Image.open(source) as frame:
            frame = frame.convert("RGB")
            if frame.width < 640 or frame.height < 360:
                raise ValueError(f"{filename} is not a full-resolution Android screenshot")
            # Preserve the actual frame's complete field of view, including HUD.
            thumbnail = ImageOps.contain(
                frame, (tile_width, tile_height), Image.Resampling.LANCZOS
            )
        col, row = i % 2, i // 2
        x = margin + col * (tile_width + gap)
        y = margin + row * (tile_height + band + gap)
        sheet.paste(thumbnail, (x + (tile_width - thumbnail.width) // 2, y))
        draw.text((x + 10, y + tile_height + 11), title.upper(),
                  fill=(221, 226, 234), font=font)
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output, optimize=True)
    print(f"Eight unaltered Android scenes assembled for visual review: {output}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("screenshots", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    assemble(
        args.screenshots,
        args.output or args.screenshots / "endless-contact-sheet.png",
    )


if __name__ == "__main__":
    main()
