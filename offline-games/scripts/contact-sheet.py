#!/usr/bin/env python3
"""Contact sheets of actual mobile Chromium screenshots for human QA review."""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import json
import math

folder = Path(__file__).resolve().parent.parent / "qa-evidence"
report = json.loads((folder / "audit.json").read_text())
items = [r for r in report["results"] if r.get("played") and (folder / r["played"]).is_file()]
try:
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    label = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 14)
except OSError:
    font = label = ImageFont.load_default()
per_sheet = 12
for page in range(math.ceil(len(items) / per_sheet)):
    chosen = items[page * per_sheet : (page+1) * per_sheet]
    image = Image.new("RGB", (4*225, 3*425+60), "#f1f4ef")
    draw = ImageDraw.Draw(image)
    draw.text((16, 12), f"NIGHT OFFLINE • Visual QA {page+1}/{math.ceil(len(items)/per_sheet)}", fill="#234d37", font=label)
    draw.text((16, 35), "Chromium mobile viewport • AFTER scripted game interaction", fill="#596e60", font=font)
    for i, r in enumerate(chosen):
        x = (i%4)*225
        y = (i//4)*425 + 60
        snap = Image.open(folder / r["played"]).convert("RGB")
        snap.thumbnail((208, 374), Image.Resampling.LANCZOS)
        image.paste(snap, (x+8+(208-snap.width)//2,y+32))
        draw.text((x+8, y+2), f"{page*per_sheet+i+1}. {r['name'][:21]}", fill="#163b2a", font=label)
        good = not r.get("errors") and not r.get("failedRequests")
        draw.text((x+8, y+406), "INPUT OK" if good else "CHECK ERRORS", fill="#297247" if good else "#a13b32", font=font)
    target = folder / f"contact-{page+1:02}.png"
    image.save(target, optimize=True)
    print(f"QA_CONTACT {target.name} {target.stat().st_size} bytes")

# A second view at 320 CSS pixels catches cramped cards, clipped controls and
# overflow invisible in screenshots from the main 390px phone viewport.
compact_items = [r for r in report["results"] if (folder/"screenshots"/(r["id"]+"-compact.png")).is_file()]
for page in range(math.ceil(len(compact_items)/per_sheet)):
    chosen = compact_items[page*per_sheet:(page+1)*per_sheet]
    image = Image.new("RGB", (4*225, 3*425+60), "#f1f4ef")
    draw = ImageDraw.Draw(image)
    draw.text((16, 12), f"NIGHT OFFLINE • Compact QA {page+1}/{math.ceil(len(compact_items)/per_sheet)}", fill="#234d37", font=label)
    draw.text((16, 35), "320 CSS px viewport • AFTER game interaction", fill="#596e60", font=font)
    for i,r in enumerate(chosen):
        x=(i%4)*225
        y=(i//4)*425+60
        snap=Image.open(folder/"screenshots"/(r["id"]+"-compact.png")).convert("RGB")
        snap.thumbnail((208,374),Image.Resampling.LANCZOS)
        image.paste(snap,(x+8+(208-snap.width)//2,y+32))
        draw.text((x+8,y+2),f"{page*per_sheet+i+1}. {r['name'][:21]}",fill="#163b2a",font=label)
        compact=r.get("measures",{}).get("compact",{})
        good=compact.get("width",999)<=compact.get("view",0)+4
        draw.text((x+8,y+406),"FITS 320px" if good else "OVERFLOW",fill="#297247" if good else "#a13b32",font=font)
    target=folder/f"compact-{page+1:02}.png"
    image.save(target,optimize=True)
    print(f"QA_COMPACT {target.name} {target.stat().st_size} bytes")
