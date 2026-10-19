"""Export the proposed occupied tiers using the checked real collection PNGs."""
import json,shutil,sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

root=Path.cwd();folder=root/(sys.argv[1] if len(sys.argv)>1 else 'dist/bleach-refined-masters')
report=json.loads((folder/'build.json').read_text());assert not report['failures'] and len(report['cards'])==85
out=root/'output/review';out.mkdir(parents=True,exist_ok=True)
fonts=root/'renderer/assets/fonts'
sans=lambda n:ImageFont.truetype(str(fonts/'Sans.ttf'),n)
display=ImageFont.truetype(str(fonts/'Display.ttf'),56)
board=Image.new('RGB',(1760,1720),'#101419');draw=ImageDraw.Draw(board)
draw.text((32,20),'NIGHT / BLEACH',font=sans(15),fill='#a9a79f')
draw.text((29,48),'Tier design proposal',font=display,fill='#f1ede4')
draw.text((32,116),'Seven occupied tiers · form-specific ranks within BLEACH',font=sans(18),fill='#a9aea9')
selected=[('ichibe-hyosube-tybw','SS','Obsidian'),('yamamoto-featured','S','Emberglass'),('byakuya-senbonzakura','A','Nocturne'),('rukia-featured','B','Tidelight'),('nanao-featured','C','Crimson'),('ichigo-substitute-soul-reaper','D','Voltage'),('kon-featured','E','Ivory')]
for i,(identity,rank,design) in enumerate(selected):
    c=next(c for c in report['cards'] if c['id']==identity+'--'+design.lower())
    assert c['power']['rank']==rank
    x=32+i*432 if i<4 else 248+(i-4)*432
    y=203 if i<4 else 983
    draw.text((x,y-40),rank+' · '+design.upper(),font=sans(25),fill='#d9d0bc')
    card=Image.open(folder/c['png']).convert('RGB').resize((400,640),Image.Resampling.LANCZOS)
    board.paste(card,(x,y))
    draw.text((x,y+652),c['name'],font=sans(17),fill='#b9c1bc')
draw.text((32,1680),'F / Vanguard awaits human artwork. SSS and EX remain open.',font=sans(17),fill='#97a4a7')
board.save(out/'NIGHT-Bleach-Tiers-2026-10-19.png',optimize=True)
shutil.copyfile(root/'dist/Bleach-Collection.html',out/'NIGHT-Bleach-Tiers-2026-10-19.html')
shutil.copyfile(root/'dist/crimson-refinement/Crimson-Kenpachi.png',out/'Crimson-Kenpachi-2026-10-19.png')
print(out)
