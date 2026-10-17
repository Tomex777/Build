"""Encode recorded browser frames; no image generation or character editing."""
from pathlib import Path
from PIL import Image
root = Path(__file__).resolve().parents[1]
frames = [Image.open(p).convert('RGB') for p in sorted((root/'qa/foil-frames').glob('*.png'))]
if not frames:
    raise SystemExit('Run node bin/present.mjs to render the light frames first.')
frames[0].save(root/'qa/foil-light.gif', save_all=True, append_images=frames[1:], duration=85, loop=0, optimize=True)
print(f'Encoded {len(frames)} actual renderer frames.')
