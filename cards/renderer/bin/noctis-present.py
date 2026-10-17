"""Comparison boards and native-detail proofs from actual Chromium renders."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageFilter
import json,sys,io,os,hashlib,math,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'dist/noctis'
out=Path(sys.argv[2]).resolve() if len(sys.argv)>2 else root/'dist/noctis-views'
qa=root/'qa/noctis-final'
out.mkdir(parents=True,exist_ok=True);qa.mkdir(parents=True,exist_ok=True)
data=json.loads((source/'renders.json').read_text())['exports'];assert len(data)==20
changed=set(sys.argv[3].split(',')) if len(sys.argv)>3 else set()
def touches(entries):return not changed or any(e['id'] in changed for e in entries)
def font(n):return ImageFont.truetype(str(root/'assets/fonts/Sans.ttf'),n)
def save(im,p):
    b=io.BytesIO();im.save(b,format='PNG');payload=b.getvalue()
    with Image.open(io.BytesIO(payload)) as test:test.load()
    q=p.with_suffix('.pending')
    with q.open('wb') as f:f.write(payload);f.flush();os.fsync(f.fileno())
    os.replace(q,p)
def card(e,w):
    with Image.open(source/e['png']) as im:
        im.load();return im.convert('RGBA').resize((w,round(w*1.6)),Image.Resampling.LANCZOS)
def paste(im,small,x,y):
    shadow=Image.new('RGBA',im.size);shadow.paste((0,0,0,42),(x+1,y+8,x+small.width-1,y+small.height+8));shadow=shadow.filter(ImageFilter.GaussianBlur(8));im.paste(shadow,(0,0),shadow);im.paste(small,(x,y),small)
for start in (0,5,10,15):
    if not touches(data[start:start+5]):continue
    im=Image.new('RGB',(2300,915),'#e3e0d7');draw=ImageDraw.Draw(im)
    draw.text((43,26),'NIGHT  /  THE NEW COLLECTION',font=font(22),fill='#253531')
    draw.text((2257,29),f'{start+1:02d} — {start+5:02d}',font=font(18),fill='#69706a',anchor='ra')
    for j,e in enumerate(data[start:start+5]):
        x=40+j*452;paste(im,card(e,412),x,84)
        draw.text((x+3,765),e['number']+'  '+e['name'].upper(),font=font(21),fill='#253531')
        draw.text((x+3,803),e['group'].upper(),font=font(12),fill='#687168')
    draw.text((43,869),'01 Obsidian preserved · 02–10 rebuilt · 11–20 additional designs',font=font(16),fill='#69706a')
    save(im,out/f'NIGHT-Collection-{start+1:02d}-{start+5:02d}.png')
for start in range(0,20,2):
    if not touches(data[start:start+2]):continue
    im=Image.new('RGB',(1360,1180),'#e3e0d7');draw=ImageDraw.Draw(im)
    for j,e in enumerate(data[start:start+2]):
        x=40+j*680;paste(im,card(e,600),x,94)
        draw.text((x+8,30),e['number']+'  '+e['name'],font=font(27),fill='#253531')
        draw.text((x+8,1090),e['group'],font=font(14),fill='#687168')
    save(im,out/f'NIGHT-Pair-{start+1:02d}-{start+2:02d}.png')
for start in (0,10):
    if not touches(data[start:start+10]):continue
    im=Image.new('RGB',(1760,1250),'#e4e2da');draw=ImageDraw.Draw(im)
    for j,e in enumerate(data[start:start+10]):
        small=card(e,320);save(small,out/f'{e["number"]}-{e["id"]}-phone-320.png')
        x=24+(j%5)*350;y=30+(j//5)*620;im.paste(small,(x,y),small)
        draw.text((x+3,y+527),e['number']+'  '+e['name'],font=font(18),fill='#253531')
        draw.text((x+3,y+561),e['group'],font=font(12),fill='#687168')
    save(im,out/f'NIGHT-Phone-Proof-{start+1:02d}-{start+10:02d}.png')
def focal(e):
    svg=ET.fromstring((source/e['svg']).read_text());image=svg.find('.//{http://www.w3.org/2000/svg}image')
    x=float(image.get('x','0'))+675*float(image.get('width'))/1154
    y=float(image.get('y','0'))+480*float(image.get('width'))/1154
    hero=next((g for g in svg.iter() if g.get('data-layer')=='hero'),None)
    if hero is not None:
        m=re.search(r'rotate\(([-.\d]+) 500 720\)',hero.get('transform',''))
        if m:
            a=math.radians(float(m[1]));dx=x-500;dy=y-720;x=500+dx*math.cos(a)-dy*math.sin(a);y=720+dx*math.sin(a)+dy*math.cos(a)
    return round(x*3),round(y*3)
for start in (0,5,10,15):
    if not touches(data[start:start+5]):continue
    im=Image.new('RGB',(2320,1000),'#dfe1d9');draw=ImageDraw.Draw(im)
    for j,e in enumerate(data[start:start+5]):
        with Image.open(source/e['png']) as img:
            img.load();img=img.convert('RGB');cx,cy=focal(e)
            tx,ty={'obsidian':(290,3830),'ghost':(2510,3120),'kinetic':(100,3310),'echelon':(90,3310),'sovereign':(600,365)}.get(e['id'],(510,3880));x=30+j*460
            draw.text((x,22),e['number']+' '+e['name'],font=font(23),fill='#253531')
            for y,crop,label in [(68,(cx-210,cy-140,cx+210,cy+140),'ART · NATIVE PIXELS'),(400,(tx,ty,tx+420,ty+220),'TYPE · NATIVE PIXELS'),(690,(0,600,420,820),'EDGE / SURFACE · NATIVE PIXELS')]:
                im.paste(img.crop(crop),(x,y));draw.text((x,y+crop[3]-crop[1]+11),label,font=font(12),fill='#5b7165')
    save(im,qa/f'native-details-{start+1:02d}-{start+5:02d}.png')
report=[]
for e in data:
    f=source/e['png']
    with Image.open(f) as im:im.load();assert im.size==(3000,4800)
    digest=hashlib.sha256(f.read_bytes()).hexdigest();assert digest==e['sha256']
    report.append({'number':e['number'],'design':e['id'],'dimensions':[3000,4800],'sha256':digest,'fullPixelDecode':True})
(qa/'exports-verification.json').write_text(json.dumps({'exports':report,'phoneProofs':{'width':320,'height':512,'count':20,'actualWhatsAppUpload':False},'nativePixelCrops':True},indent=2))
print('Saved 4 overviews, 10 pairs, 20 phone proofs and 4 native-pixel detail boards.')
