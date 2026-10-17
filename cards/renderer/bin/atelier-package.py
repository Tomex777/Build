"""Package all final cards and a complete reusable source snapshot."""
from pathlib import Path
from PIL import Image
import sys,os,zipfile,hashlib,json
root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'dist/deliverables'
out.mkdir(parents=True,exist_ok=True)
def publish(data,p):
    q=p.with_suffix(p.suffix+'.pending')
    with q.open('wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
    os.replace(q,p)
def archive(name,items):
    dest=out/name;tmp=out/(name+'.pending')
    with zipfile.ZipFile(tmp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for f,relative in sorted(items,key=lambda q:q[1]):z.write(f,relative)
    with zipfile.ZipFile(tmp) as z:
        assert z.testzip() is None
        count=len(z.namelist())
    os.replace(tmp,dest)
    return {'file':dest.name,'bytes':dest.stat().st_size,'files':count,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'crcValid':True}
entries=json.loads((root/'dist/atelier/renders.json').read_text())['exports']
assert len(entries)==10
for e in entries:
    f=root/'dist/atelier'/e['png']
    with Image.open(f) as im:im.load();assert im.size==(3000,4800)
    assert hashlib.sha256(f.read_bytes()).hexdigest()==e['sha256']
views=root/'dist/atelier-views'
for f in views.glob('NIGHT-*.png'):
    publish(f.read_bytes(),out/f.name)
publish((root/'dist/NIGHT-Ten-Designs.html').read_bytes(),out/'NIGHT-Ten-Designs.html')
cards=[]
for f in (root/'dist/atelier').iterdir():
    if f.suffix in ('.png','.svg','.json'):cards.append((f,'NIGHT-Ten-Designs/'+('cards/' if f.suffix=='.png' else 'svg/' if f.suffix=='.svg' else '')+f.name))
cards.extend((f,'NIGHT-Ten-Designs/previews/'+f.name) for f in views.glob('*.png'))
cards.append((root/'ATELIER-START-HERE.md','NIGHT-Ten-Designs/READ-ME.md'))
source=[]
for directory in ('src','bin','examples','assets','tests','docs'):
    source.extend((f,'NIGHT-Ten-Designs/'+str(f.relative_to(root))) for f in (root/directory).rglob('*') if f.is_file())
for f in root.iterdir():
    if f.is_file() and (f.suffix in ('.md','.json') or f.name=='LICENSE'):source.append((f,'NIGHT-Ten-Designs/'+f.name))
for directory in ('qa/atelier-final','qa/atelier-01-views','qa/atelier-02-views'):
    for f in (root/directory).glob('*'):
        if f.is_file() and (directory=='qa/atelier-final' or f.name.startswith('NIGHT-Designs-')):source.append((f,'NIGHT-Ten-Designs/'+str(f.relative_to(root))))
source.append((root/'qa/atelier-preserved-baseline.json','NIGHT-Ten-Designs/qa/atelier-preserved-baseline.json'))
source.extend((f,'NIGHT-Ten-Designs/dist/atelier/'+f.name) for f in (root/'dist/atelier').iterdir() if f.suffix in ('.svg','.json'))
source.append((root/'dist/NIGHT-Ten-Designs.html','NIGHT-Ten-Designs/dist/NIGHT-Ten-Designs.html'))
source.extend((f,'NIGHT-Ten-Designs/dist/atelier-views/'+f.name) for f in views.glob('*.png'))
report=[archive('NIGHT-Ten-Designs-4K.zip',cards),archive('NIGHT-Ten-Designs-Source.zip',source)]
publish(json.dumps(report,indent=2).encode(),out/'NIGHT-Ten-Designs-Manifest.json')
print(json.dumps(report,indent=2))
