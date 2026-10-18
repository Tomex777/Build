"""Create the visual review book from a complete rendered collection."""
import json,sys,hashlib,math
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import ImageReader
from reportlab.lib.pagesizes import A4

folder=Path(sys.argv[1] if len(sys.argv)>1 else 'dist/collection-review-masters')
destination=Path(sys.argv[2] if len(sys.argv)>2 else 'output/pdf/Bleach-Collection.pdf')
report=json.loads((folder/'build.json').read_text())
assert report['width']==3000 and report['cards'] and not report['failures'],'Use a complete full-resolution build'
ranks=['F','E','D','C','B','A','S','SS','SSS']
cards=sorted(report['cards'],key=lambda c:(-ranks.index(c['power']['rank']),c['number'],c['design']))
for card in cards:
 assert hashlib.sha256((folder/card['preview']).read_bytes()).hexdigest()==card['previewSHA256'],'Changed gallery image'
destination.parent.mkdir(parents=True,exist_ok=True)
pdf=canvas.Canvas(str(destination),pagesize=A4,pageCompression=1)
pdf.setTitle('NIGHT / BLEACH - The collection');pdf.setAuthor('NIGHT');pdf.setSubject('85 collectible editions, form power grades and distinct artwork')
width,height=A4;margin=30;gap=15;cardwidth=(width-margin*2-gap*2)/3;cardheight=cardwidth*1.6;pages=math.ceil(len(cards)/6)
def clean(value):return value.replace('\u2014','-').replace('\u2013','-').replace('\u2019',"'")
def text(x,y,value,size=8,color='#d9ddda',font='Helvetica',limit=None):
 value=clean(str(value));s=size
 if limit:
  while pdf.stringWidth(value,font,s)>limit and s>5:s-=.1
 pdf.setFillColor(HexColor(color));pdf.setFont(font,s);pdf.drawString(x,y,value)
for page in range(pages):
 pdf.setFillColor(HexColor('#101419'));pdf.rect(0,0,width,height,fill=1,stroke=0)
 text(margin,height-34,'NIGHT / BLEACH',7,'#aaa99f')
 text(margin,height-66,'The collection',25,'#f1ede4','Times-Roman')
 text(width-130,height-61,f'{page*6+1:02}-{min(page*6+6,len(cards)):02} / {len(cards)}',8,'#aaa99f')
 pdf.setStrokeColor(HexColor('#30383c'));pdf.setLineWidth(.5);pdf.line(margin,height-84,width-margin,height-84)
 for slot,card in enumerate(cards[page*6:page*6+6]):
  col=slot%3;row=slot//3;x=margin+col*(cardwidth+gap);top=height-104-row*341;y=top-cardheight
  pdf.drawImage(ImageReader(str(folder/card['preview'])),x,y,width=cardwidth,height=cardheight)
  pdf.linkURL(card['source'],(x,y,x+cardwidth,y+cardheight),relative=0,thickness=0)
  text(x,y-15,card['name'],8.5,'#f1ede4','Helvetica',cardwidth)
  text(x,y-28,card['form'],6.4,'#aab4b1','Helvetica',cardwidth)
  text(x,y-41,f"{card['design'].upper()}  /  {card['power']['rank']}",6.3,'#c6b79c','Helvetica',cardwidth)
 pdf.setStrokeColor(HexColor('#30383c'));pdf.line(margin,49,width-margin,49)
 text(margin,34,'NIGHT editorial grades for the depicted forms.',6,'#879397')
 text(margin,23,'Cards link to their official artwork sources.',6,'#879397')
 text(width-63,29,f'{page+1:02} / {pages:02}',6,'#aaa99f')
 pdf.showPage()
pdf.save()
print(f'Saved {len(cards)} cards over {pages} pages: {destination}')
