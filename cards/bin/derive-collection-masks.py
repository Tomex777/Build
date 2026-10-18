# Optional artwork-authoring utility. Requires opencv-python-headless 4.10.0.84 and numpy 2.1.3 in .cache/mask-tools.
# This regenerates alpha mattes from original pixels, not character illustrations.
import sys,json,hashlib,html
from pathlib import Path
sys.path.insert(0,str(Path('.cache/mask-tools').resolve()))
import cv2,numpy as np
from PIL import Image,ImageDraw
root=Path('.cache/collection-research');root.mkdir(parents=True,exist_ok=True);catpath=Path('catalog/bleach.json');cat=json.loads(catpath.read_text());guides=json.loads(Path('artwork/masks/collection-guides.json').read_text())
for name,polys in guides.items():
 card=next(c for c in cat['cards'] if c['id']==name+'-featured');x,y,w,h=card['recipe']['crop'];orig=np.array(Image.open(card['source']['localPath']).convert('RGBA').crop((x,y,x+w,y+h)))
 guide=np.zeros((h,w),np.uint8)
 points=[]
 for poly in polys:
  pts=np.array([[px-x,(y+h if py>=750 else py)-y] for px,py in poly],np.int32);points.append(pts);cv2.fillPoly(guide,[pts],255)
 sure=cv2.erode(guide,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(45,45)))
 probable=cv2.dilate(guide,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(65,65)))
 labels=np.where(guide>0,cv2.GC_PR_FGD,cv2.GC_PR_BGD).astype('uint8');labels[probable==0]=cv2.GC_BGD;labels[sure>0]=cv2.GC_FGD;labels[orig[:,:,3]<3]=cv2.GC_BGD
 holes={'mashiro':[[183,430],[296,432],[259,455],[188,472]],'kensei':[[265,547],[310,581],[339,634],[319,750],[152,750],[216,684]]}
 if name in holes:cv2.fillPoly(labels,[np.array(holes[name],np.int32)-[x,y]],cv2.GC_BGD)
 cv2.grabCut(orig[:,:,:3],labels,None,np.zeros((1,65)),np.zeros((1,65)),5,cv2.GC_INIT_WITH_MASK)
 alpha=np.where((labels==cv2.GC_FGD)|(labels==cv2.GC_PR_FGD),255,0).astype('uint8');alpha[orig[:,:,3]<3]=0
 yy,xx=np.indices((h,w));r,g,b=[orig[:,:,i].astype('int16') for i in range(3)]
 unwanted={'ginjo':(g>210)&(g>r+65)&(g>b+25)&(yy<610)&(xx>360),'kensei':(g>r+18)&(b>r+22),'hisagi':(g>r+6)&(b>r+13)&(r>100)&(yy<340),'kaien':(r>150)&(g>170)&(b>200)&(yy<330),'mashiro':(r>225)&(g>205)&(b<185)}
 if name in unwanted:
  n,labels=cv2.connectedComponents(unwanted[name].astype('uint8'),8)
  remove=np.unique(labels[(alpha==0)&unwanted[name]]);remove=remove[remove!=0];alpha[np.isin(labels,remove)]=0
 if name=='ginjo':
  # Preserve the actual cross-blade and handle, removing only exterior glow.
  weapon=[[[319,77],[337,71],[350,81],[355,92],[479,166],[492,174],[498,114],[502,32],[510,6],[521,1],[533,8],[541,2],[548,7],[553,22],[563,22],[568,43],[554,48],[551,68],[540,78],[538,129],[535,175],[558,171],[565,178],[566,191],[750,301],[750,327],[570,223],[566,244],[750,352],[750,514],[732,500],[720,470],[690,470],[689,457],[546,377],[540,407],[532,432],[522,447],[504,451],[491,455],[481,448],[477,428],[483,389],[485,335],[483,283],[484,235],[465,218],[453,209],[440,199],[421,182],[404,167],[396,150],[379,143],[368,134],[353,124],[347,109],[335,106],[324,100],[324,89]]]
  for poly in weapon:cv2.fillPoly(alpha,[np.array(poly,np.int32)],255)
 if name=='hisagi':
  alpha[(yy<175)&(xx>250)&(r>110)]=0
  def bez(a,b,c,d):
   ts=np.linspace(0,1,80)[:,None];return (1-ts)**3*np.array(a)+3*(1-ts)**2*ts*np.array(b)+3*(1-ts)*ts**2*np.array(c)+ts**3*np.array(d)
  blade=np.vstack(([[181,0],[470,0]],bez([470,0],[560,37],[661,97],[690,142]),bez([690,142],[715,198],[510,145],[181,0]))).astype('int32');cv2.fillPoly(alpha,[blade],255)
 n,l,stats,_=cv2.connectedComponentsWithStats(alpha,8)
 for i in range(1,n):
  if stats[i,cv2.CC_STAT_AREA]<80:alpha[l==i]=0
 contours,hierarchy=cv2.findContours(alpha,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE);paths=[]
 for contour in contours:
  if abs(cv2.contourArea(contour))<3:continue
  pts=cv2.approxPolyDP(contour,.55,True).reshape(-1,2);paths.append('M'+'L'.join(f'{a} {b}' for a,b in pts)+'Z')
 meta={'source':card['source']['localPath'],'sourceSHA256':card['source']['sha256'],'nativeDimensions':[card['source']['width'],card['source']['height']],'crop':card['recipe']['crop'],'construction':'Source-guided polygons, traditional GrabCut, colour-connected cleanup and manual blade guide. Alpha matte only; no generated or replacement artwork pixels.','guides':polys,'rights':card['source']['rights']}
 svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><title>{html.escape(card["name"])} source-bound character matte</title><metadata>{html.escape(json.dumps(meta,separators=(",",":")))}</metadata><path fill="white" fill-rule="evenodd" d="{"".join(paths)}"/></svg>\n'
 dest=Path('artwork/masks')/(name+'-character.svg');dest.write_text(svg)
 card['recipe']['depthLayers']={'nocturne':{'mask':{'localPath':dest.as_posix(),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'sourceSHA256':card['source']['sha256'],'crop':card['recipe']['crop']},'auraOpacity':.32,'cropEdgeFade':.025}}
 cut=orig.copy();cut[:,:,3]=np.minimum(alpha,orig[:,:,3]);bg=Image.new('RGB',(w,h),'#1c242b');im=Image.fromarray(cut);bg.paste(im,(0,0),im);bg.save(root/(name+'-matte.jpg'),quality=96)
 print(name,len(paths),'contours',round(np.count_nonzero(alpha)/(w*h),3))
catpath.write_text(json.dumps(cat,indent=2,ensure_ascii=False)+'\n')
