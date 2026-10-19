"""Build internal native-pixel and phone inspection boards from real masters."""
import json,sys
from pathlib import Path
from PIL import Image,ImageDraw

folder=Path(sys.argv[1] if len(sys.argv)>1 else 'dist/bleach-refined-masters')
report=json.loads((folder/'build.json').read_text())
out=Path('.cache/master-review');out.mkdir(parents=True,exist_ok=True)
for start in range(0,len(report['cards']),6):
    board=Image.new('RGB',(2250,1590),'#10151a');draw=ImageDraw.Draw(board)
    for i,card in enumerate(report['cards'][start:start+6]):
        master=Image.open(folder/card['png']).convert('RGB')
        cx,cy=[round(v*report['width']/1000) for v in card['qa']['focalPosition']]
        x=max(0,min(master.width-750,cx-375));y=max(0,min(master.height-750,cy-375))
        board.paste(master.crop((x,y,x+750,y+750)),((i%3)*750,(i//3)*795))
        draw.text(((i%3)*750+12,(i//3)*795+761),card['id'],fill='#e5ddca')
    board.save(out/f'native-{start//6+1:02}.jpg',quality=97,subsampling=0)
for p in folder.glob('contact-*.png'):Image.open(p).convert('RGB').save(out/f'{p.stem}.jpg',quality=95,subsampling=0)
print(out)
