import sys,json,zipfile,hashlib
from pathlib import Path
folder=Path(sys.argv[1]);output=Path(sys.argv[2]);m=json.loads((folder/'manifest.json').read_text())
assert m['cards'] and all(c.get('approved') and c.get('review',{}).get('status')=='approved' for c in m['cards']), 'Only reviewed cards can be packaged'
files={'manifest.json','index.html',*m.get('boards',[])}
for c in m['cards']:
 assert hashlib.sha256((folder/c['png']).read_bytes()).hexdigest()==c['pngSHA256']
 files.update([c['png'],c['svg'],c['phone']])
assert all(Path(f).name==f for f in files)
output.parent.mkdir(parents=True,exist_ok=True);pending=Path(str(output)+'.pending')
with zipfile.ZipFile(pending,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
 for f in sorted(files):z.write(folder/f,f)
with zipfile.ZipFile(pending) as z: assert z.testzip() is None
pending.replace(output)
checksum=hashlib.sha256(output.read_bytes()).hexdigest()
Path(str(output)+'.sha256').write_text(checksum+'  '+output.name+'\n')
print(f'Packaged {len(m["cards"])} cards: {output} ({output.stat().st_size:,} bytes)')
