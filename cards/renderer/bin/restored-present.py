"""Build review boards from the actual full-resolution restored exports."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import sys

root = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'dist/restored'
qa = root / 'qa/restored-final'
qa.mkdir(parents=True, exist_ok=True)
font = lambda size: ImageFont.truetype('DejaVuSans.ttf', size)
ink, muted, paper = '#193b40', '#57696b', '#e4e5df'
images = [Image.open(out / f'ichigo-{name}-restored.png').convert('RGBA') for name in ['nocturne', 'tidelight']]
for img in images:
    assert img.size == (3000, 4800)

board = Image.new('RGB', (1560, 1300), paper)
d = ImageDraw.Draw(board)
d.text((54, 32), 'NIGHT / RESTORED EDITIONS', font=font(24), fill=ink)
for i, (img, title, detail) in enumerate(zip(images, ['NOCTURNE', 'TIDELIGHT'], ['Circular disc removed', 'Pearl foil'])):
    x = 54 + i * 752
    card = img.resize((700, 1120), Image.Resampling.LANCZOS)
    board.paste(card, (x, 90), card)
    d.text((x, 1230), title, font=font(23), fill=ink)
    d.text((x, 1263), detail, font=font(16), fill=muted)
board.save(out / 'NIGHT-Restored-Pair.png')

phone = Image.new('RGB', (760, 620), paper)
d = ImageDraw.Draw(phone)
d.text((35, 20), '320 × 512 / PHONE VIEWING PROOF', font=font(17), fill=ink)
for i, (img, name) in enumerate(zip(images, ['Nocturne', 'Tidelight'])):
    x = 35 + i * 370
    card = img.resize((320, 512), Image.Resampling.LANCZOS)
    phone.paste(card, (x, 60), card)
    d.text((x, 586), name, font=font(16), fill=ink)
phone.save(qa / 'phone-proof.png')

# These are 1:1 crops from the finished PNG, with no resizing.
details = Image.new('RGB', (1380, 790), paper)
d = ImageDraw.Draw(details)
labels = ['Artwork / native pixels', 'Typography / native pixels', 'Frame / native pixels']
regions = [(1340, 1650, 1760, 1920), (200, 3910, 620, 4180), (20, 75, 440, 345)]
for row, (img, name) in enumerate(zip(images, ['Nocturne', 'Tidelight'])):
    y = 65 + row * 385
    d.text((30, y - 40), name, font=font(22), fill=ink)
    for col, (label, box) in enumerate(zip(labels, regions)):
        x = 30 + col * 450
        crop = img.crop(box)
        details.paste(crop, (x, y), crop)
        d.text((x, y + 286), label, font=font(15), fill=muted)
details.save(qa / 'native-details.png')
print('Saved restored pair, phone proof and native-pixel detail board.')
