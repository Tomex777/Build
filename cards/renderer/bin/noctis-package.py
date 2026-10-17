"""Package verified cards, offline gallery and complete source/history."""
from pathlib import Path
from PIL import Image
import os,sys,json,hashlib,zipfile
root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'dist/noctis-deliverables'
out.mkdir(parents=True,exist_ok=True)
def publish(data,p):
    tmp=p.with_suffix(p.suffix+'.pending')
    with tmp.open('wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
    os.replace(tmp,p)
def archive(name,items):
    dest=out/name;tmp=out/(name+'.pending');seen=set()
    with zipfile.ZipFile(tmp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for f,relative in sorted(items,key=lambda x:x[1]):
            assert relative not in seen;seen.add(relative);z.write(f,relative)
    with zipfile.ZipFile(tmp) as z:assert z.testzip() is None;count=len(z.namelist())
    os.replace(tmp,dest)
    return {'file':dest.name,'files':count,'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'crcValid':True}
entries=json.loads((root/'dist/noctis/renders.json').read_text())['exports'];assert len(entries)==20
for e in entries:
    f=root/'dist/noctis'/e['png']
    with Image.open(f) as im:im.load();assert im.size==(3000,4800)
    assert hashlib.sha256(f.read_bytes()).hexdigest()==e['sha256']
report=json.loads((root/'qa/noctis-final/verification.json').read_text())
assert report['preservation']['obsidianByteIdentical'] and report['preservation']['allUnchanged']
assert len(report['profiles'])==40 and report['gallery']['pageErrors']==[]
views=root/'dist/noctis-views'
for f in views.glob('NIGHT-Collection-*.png'):publish(f.read_bytes(),out/f.name)
publish((root/'dist/NIGHT-Noctis-Collection.html').read_bytes(),out/'NIGHT-Collection.html')
publish((root/'preserved/obsidian-v1/01-obsidian-ichigo.png').read_bytes(),out/'NIGHT-Obsidian-Preserved-4K.png')
cards=[]
for f in (root/'dist/noctis').iterdir():
    if f.suffix in ('.png','.svg','.json'):
        sub='cards/' if f.suffix=='.png' else 'svg/' if f.suffix=='.svg' else ''
        cards.append((f,'NIGHT-Collection/'+sub+f.name))
cards.extend((f,'NIGHT-Collection/previews/'+f.name) for f in views.glob('*.png'))
cards.append((root/'NOCTIS-START-HERE.md','NIGHT-Collection/READ-ME.md'))
cards.append((root/'dist/NIGHT-Noctis-Collection.html','NIGHT-Collection/NIGHT-Collection.html'))
source=[]
for directory in ('src','bin','examples','assets','tests','docs','preserved/obsidian-v1','qa/noctis-final'):
    source.extend((f,'NIGHT-Collection-Source/'+str(f.relative_to(root))) for f in (root/directory).rglob('*') if f.is_file())
for f in root.iterdir():
    if f.is_file() and (f.suffix in ('.md','.json') or f.name=='LICENSE'):source.append((f,'NIGHT-Collection-Source/'+f.name))
for f in root.glob('qa/*preserved-baseline.json'):source.append((f,'NIGHT-Collection-Source/qa/'+f.name))
for directory in ('dist/atelier','dist/noctis'):
    source.extend((f,'NIGHT-Collection-Source/'+str(f.relative_to(root))) for f in (root/directory).iterdir() if f.suffix in ('.svg','.json'))
source.append((root/'dist/NIGHT-Noctis-Collection.html','NIGHT-Collection-Source/dist/NIGHT-Noctis-Collection.html'))
source.extend((f,'NIGHT-Collection-Source/dist/noctis-views/'+f.name) for f in views.glob('*.png'))
results=[archive('NIGHT-Collection-4K.zip',cards),archive('NIGHT-Collection-Source.zip',source)]
manifest={'cards':20,'preserved':1,'rebuilt':9,'additional':10,'rasterDimensions':[3000,4800],'archives':results}
publish(json.dumps(manifest,indent=2).encode(),out/'NIGHT-Collection-Manifest.json')
print(json.dumps(manifest,indent=2))
