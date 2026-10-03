"""Download publisher-authored models unchanged. VRM 0 is a binary glTF container.
Renaming its extension to .glb makes it available to Mise's glTF intake; no geometry,
rig, texture, or pose is generated or modified here.
"""
from pathlib import Path
import subprocess,hashlib,json
root=Path(__file__).resolve().parents[2]
target=root/'app/src/androidTest/assets/anime-tree';target.mkdir(parents=True,exist_ok=True)
for asset in json.loads(Path(__file__).with_name('assets.json').read_text()):
 p=target/(asset['name']+'.glb')
 if not p.exists() or hashlib.sha256(p.read_bytes()).hexdigest()!=asset['sha256']:
  subprocess.run(['curl','--fail','--location','--retry','3','--max-time','120',asset['url'],'--output',str(p)],check=True)
 assert hashlib.sha256(p.read_bytes()).hexdigest()==asset['sha256'],asset['name']
 print('Verified',asset['name'],p.stat().st_size)
