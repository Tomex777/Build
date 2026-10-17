"""Create comparisons and phone-size proofs from actual rendered card PNGs."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import sys
import io
import os

root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'dist/tall-views'
out.mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype(str(root/'assets/fonts/Sans.ttf'),23)

def save_png(image,path):
    encoded=io.BytesIO()
    image.save(encoded,format='PNG')
    payload=encoded.getvalue()
    with Image.open(io.BytesIO(payload)) as check: check.load()
    pending=path.with_suffix('.png.pending')
    with pending.open('wb') as f:
        f.write(payload)
        f.flush()
        os.fsync(f.fileno())
    os.replace(pending,path)

def card(path,width,height):
    with Image.open(path) as im:
        im.load()
        return im.convert('RGBA').resize((width,height),Image.Resampling.LANCZOS)

for character in ('ichigo','naruto'):
    canvas=Image.new('RGB',(1280,1080),'#cbd0ca')
    draw=ImageDraw.Draw(canvas)
    for index,edition in enumerate(('nocturne','tidelight')):
        source=root/f'dist/tall/{character}-{edition}-1600-3000.png'
        im=card(source,600,960)
        canvas.paste(im,(25+630*index,70),im)
        draw.text((40+630*index,24),edition.title(),font=font,fill='#183441')
        save_png(card(source,320,512),out/f'{character}-{edition}-phone-320.png')
    save_png(canvas,out/f'NIGHT-Tall-{character.title()}.png')

canvas=Image.new('RGB',(1280,1080),'#cbd0ca')
draw=ImageDraw.Draw(canvas)
for index,(height,label) in enumerate(((1400,'Current proportions · 5:7'),(1600,'Taller proportions · 5:8'))):
    source=root/'qa/tall-01/ichigo-nocturne-1400-1500.png' if height==1400 else root/'dist/tall/ichigo-nocturne-1600-3000.png'
    im=card(source,600,round(height*.6))
    canvas.paste(im,(25+630*index,70),im)
    draw.text((40+630*index,24),label,font=font,fill='#183441')
save_png(canvas,out/'NIGHT-Tall-Comparison.png')
print('Created comparison, both character overviews and four 320×512 viewing proofs.')
