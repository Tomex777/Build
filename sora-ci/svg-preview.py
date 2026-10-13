#!/usr/bin/env python3
"""Render Sora's source SVGs and assert Android VectorDrawables exactly match the art."""
from __future__ import annotations

from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image, ImageDraw, ImageFont
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "sora-overlay/app/src/main/assets/icons"
NATIVE = ROOT / "sora-overlay/app/src/main/res/drawable"
OUT = ROOT / "artifacts"
OUT.mkdir(exist_ok=True)
NAMES = ("snake", "tiles", "tictactoe", "memory", "daily", "who", "quotes", "bible", "rather", "meme", "quiz")
A = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"
sizes = (40, 96, 256)

def fail(message: str):
    raise SystemExit("SVG ART VALIDATION FAILURE: " + message)

def validate_pair(name: str):
    src = SOURCE / f"sora_game_{name}.svg"
    xml = NATIVE / f"sora_game_{name}.xml"
    if not src.exists() or not xml.exists():
        fail(f"missing SVG or Android vector for {name}")
    svg = ET.parse(src).getroot()
    native = ET.parse(xml).getroot()
    if svg.get("viewBox") != "0 0 64 64":
        fail(f"incorrect viewBox for {name}")
    if native.attrib.get(A+"viewportWidth") != "64" or native.attrib.get(A+"viewportHeight") != "64":
        fail(f"incorrect native viewport for {name}")
    paths = list(svg.findall(SVG+"path"))
    other = list(native.findall("path"))
    if not paths or len(paths) != len(other):
        fail(f"different vector path counts for {name}: {len(paths)} vs {len(other)}")
    for index,(s,n) in enumerate(zip(paths,other)):
        expected = {
            "d": n.attrib.get(A+"pathData"),
            "fill": n.attrib.get(A+"fillColor","none"),
            "stroke": n.attrib.get(A+"strokeColor","none"),
            "width": n.attrib.get(A+"strokeWidth"),
        }
        expected["fill"] = "none" if expected["fill"] == "@android:color/transparent" else expected["fill"]
        actual = {key: s.attrib.get(key) for key in ("d","fill","stroke")}
        actual["width"] = s.attrib.get("stroke-width")
        for key in actual:
            if (actual[key] or "none") != (expected[key] or "none"):
                fail(f"{name} path {index}: {key} mismatch: {actual[key]} vs {expected[key]}")
    for size in sizes:
        dest = OUT / f"{name}-{size}px.png"
        cairosvg.svg2png(url=str(src),write_to=str(dest),output_width=size,output_height=size)
        img = Image.open(dest).convert("RGBA")
        if img.getbbox() is None:
            fail(f"{name} looks blank at {size}px")
        # Avoid icons with the entire image same solid color (bad silhouette).
        pixels=img.getdata()
        if len(set(pixels)) < 4:
            fail(f"{name} has no visible detail at {size}px")
    print(f"OK: {name} paths={len(paths)} sizes={sizes}")

for name in NAMES:
    validate_pair(name)

surface = Image.new("RGB",(4*304,3*4*332),(16,16,15))
draw = ImageDraw.Draw(surface)
for row,size in enumerate(sizes):
    for index,name in enumerate(NAMES):
        col=index%4
        tile_row=row*4+index//4
        icon=Image.open(OUT/f"{name}-{size}px.png").convert("RGBA")
        draw.rounded_rectangle((col*304+14,tile_row*332+14,col*304+290,tile_row*332+316),radius=20,fill=(31,31,28))
        width,height=icon.size
        fitted=icon.copy()
        if size>220:
            fitted.thumbnail((222,222))
        elif size<80:
            fitted=fitted.resize((120,120))
        else:
            fitted=fitted.resize((180,180))
        x=col*304+(304-fitted.width)//2
        y=tile_row*332+30+(235-fitted.height)//2
        surface.paste(fitted,(x,y),fitted)
        draw.text((col*304+28,tile_row*332+271),f"{name.upper()}  -  {size}px source",fill=(233,185,73))
surface.save(OUT/"sora-svg-contact-sheet.png")
print("Created artifacts/sora-svg-contact-sheet.png")
