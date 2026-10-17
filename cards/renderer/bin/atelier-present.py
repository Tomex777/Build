"""Presentation boards and phone proofs from actual Chromium card renders."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageFilter
import json,sys,io,os
root=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'dist/atelier'
out=Path(sys.argv[2]).resolve() if len(sys.argv)>2 else root/'dist/atelier-views'
out.mkdir(parents=True,exist_ok=True)
data=json.loads((source/'renders.json').read_text())['exports']
font_path=root/'assets/fonts/Sans.ttf'
def font(n):return ImageFont.truetype(str(font_path),n)
def save(im,p):
    b=io.BytesIO();im.save(b,format='PNG');payload=b.getvalue()
    with Image.open(io.BytesIO(payload)) as test:test.load()
    q=p.with_suffix('.pending');q.write_bytes(payload)
    with q.open('rb') as f:os.fsync(f.fileno())
    os.replace(q,p)
def card(e,w):
    with Image.open(source/e['png']) as im:
        im.load();return im.convert('RGBA').resize((w,round(w*1.6)),Image.Resampling.LANCZOS)
def paste(im,small,x,y):
    shadow=Image.new('RGBA',im.size);shadow.paste((0,0,0,50),(x+1,y+9,x+small.width-1,y+small.height+9));shadow=shadow.filter(ImageFilter.GaussianBlur(9));im.paste(shadow,(0,0),shadow);im.paste(small,(x,y),small)
for start in (0,5):
    im=Image.new('RGB',(2300,925),'#e3e0d7');draw=ImageDraw.Draw(im)
    draw.text((43,25),'NIGHT  /  TEN NEW DIRECTIONS',font=font(22),fill='#253531')
    draw.text((2257,28),f'{start+1:02d} — {start+5:02d}',font=font(18),fill='#69706a',anchor='ra')
    for j,e in enumerate(data[start:start+5]):
        x=40+j*452;paste(im,card(e,412),x,83)
        draw.text((x+3,766),e['number']+'  '+e['name'].upper(),font=font(21),fill='#253531')
        draw.text((x+3,804),e['material'].split(' / ')[0],font=font(12),fill='#687168')
    draw.text((43,879),'Same illustration. Different composition, framing and materials.',font=font(16),fill='#69706a')
    save(im,out/f'NIGHT-Designs-{start+1:02d}-{start+5:02d}.png')
for start in range(0,len(data),2):
    im=Image.new('RGB',(1360,1170),'#dfded5');draw=ImageDraw.Draw(im)
    for j,e in enumerate(data[start:start+2]):
        x=40+j*680;paste(im,card(e,600),x,94)
        draw.text((x+8,30),e['number']+'  '+e['name'],font=font(27),fill='#253531')
        draw.text((x+8,1090),e['material'],font=font(13),fill='#687168')
    save(im,out/f'NIGHT-Pair-{start+1:02d}-{min(start+2,len(data)):02d}.png')
phone=Image.new('RGB',(1760,1250),'#e4e2da');draw=ImageDraw.Draw(phone)
for i,e in enumerate(data):
    small=card(e,320);save(small,out/f'{e["number"]}-{e["id"]}-phone-320.png')
    x=24+(i%5)*350;y=55+(i//5)*620
    phone.paste(small,(x,y),small)
    draw.text((x+3,y+527),e['number']+' '+e['name'],font=font(18),fill='#253531')
save(phone,out/'NIGHT-All-Ten-Phone-Proof.png')
print('Saved two overviews, five comparison pairs, and ten 320×512 phone proofs.')
