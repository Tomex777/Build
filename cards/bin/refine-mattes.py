"""Author source-bound alpha mattes using traditional GrabCut and manual guides.

Install opencv-python-headless==4.10.0.84 and numpy==2.1.3 into .cache/mask-tools.
Run from cards/. RGB values always come from the original pinned illustration.
"""
import sys
from pathlib import Path
sys.path.insert(0,str(Path('.cache/mask-tools').resolve()))
import cv2
import numpy as np
from PIL import Image,ImageDraw
import json,hashlib,html

root=Path.cwd()
cf=root/'catalog/bleach.json'
catalogue=json.loads(cf.read_text())
guides=json.loads((root/'artwork/masks/refinement-guides.json').read_text())
preview=root/'.cache/refinement-mattes'
preview.mkdir(parents=True,exist_ok=True)
for identity,polygons in guides.items():
    card=next(c for c in catalogue['cards'] if c['id']==identity)
    x,y,w,h=card['recipe']['crop']
    original=np.array(Image.open(root/card['source']['localPath']).convert('RGBA').crop((x,y,x+w,y+h)))
    guide=Image.new('L',(w,h));draw=ImageDraw.Draw(guide)
    for polygon in polygons:draw.polygon([tuple(p) for p in polygon],fill=255)
    guide=np.array(guide)
    labels=np.where(guide>0,cv2.GC_PR_FGD,cv2.GC_PR_BGD).astype('uint8')
    sure=cv2.erode(guide,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(31,31)))
    band=cv2.dilate(guide,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(35,35)))
    labels[band==0]=cv2.GC_BGD;labels[sure>0]=cv2.GC_FGD;labels[original[:,:,3]<3]=cv2.GC_BGD
    if identity=='hirako-featured':
        hole=np.zeros((h,w),dtype='uint8')
        cv2.fillPoly(hole,[np.array([[328,535],[358,558],[380,580],[369,600],[345,644],[328,690],[301,715],[304,650],[301,608],[293,571]],dtype='int32')],255)
        labels[hole>0]=cv2.GC_BGD
    if identity=='stark-featured':
        labels[500:750,515:610]=cv2.GC_PR_BGD
        roi=original[500:750,515:610,:3].astype('int16')
        blue=(roi[:,:,0]<100)&(roi[:,:,2]>130)&(roi[:,:,2]>roi[:,:,0]+60)
        labels[500:750,515:610][blue]=cv2.GC_BGD
    cv2.setRNGSeed(21)
    cv2.grabCut(cv2.cvtColor(original[:,:,:3],cv2.COLOR_RGB2BGR),labels,None,np.zeros((1,65),np.float64),np.zeros((1,65),np.float64),6,cv2.GC_INIT_WITH_MASK)
    alpha=np.where((labels==cv2.GC_FGD)|(labels==cv2.GC_PR_FGD),255,0).astype('uint8')
    if identity=='riruka-featured':
        for poly in [
            [[539,361],[558,382],[541,447],[504,515],[467,574],[455,615],[422,645],[391,648],[337,620],[280,599],[276,578],[291,543],[323,513],[364,471],[406,428],[448,395],[497,369]],
            [[270,581],[285,550],[314,534],[342,526],[377,522],[410,530],[440,549],[457,575],[459,603],[446,632],[422,650],[390,663],[352,667],[315,657],[285,640],[273,613]]
        ]:cv2.fillPoly(alpha,[np.array(poly,dtype='int32')],255)
    R,G,B=[original[:,:,i].astype('float32') for i in range(3)]
    yy,xx=np.mgrid[:h,:w]
    if identity=='kenpachi-featured':alpha[(R>240)&(G>220)&(B<G-12)&(((yy<260)&(xx<320))|((yy>170)&(yy<370)&(xx>500))|((yy>650)&(xx<120)))]=0
    if identity=='byakuya-featured':alpha[(R>165)&(B>140)&(G<R*.94)&(B>G*1.06)]=0
    if identity=='hirako-featured':alpha[(R>220)&(G>190)&(B<100)&(yy>525)&(xx>280)&(xx<390)]=0
    alpha[original[:,:,3]<3]=0
    count,components,stats,_=cv2.connectedComponentsWithStats(alpha,8)
    for i in range(1,count):
        if stats[i,cv2.CC_STAT_AREA]<45:alpha[components==i]=0
    contours,_=cv2.findContours(alpha,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE)
    paths=[]
    for contour in contours:
        if cv2.contourArea(contour)<8:continue
        points=cv2.approxPolyDP(contour,.4,True).reshape(-1,2)
        paths.append('M'+'L'.join(f'{px},{py}' for px,py in points)+'Z')
    metadata={'source':card['source']['localPath'],'sourceSHA256':card['source']['sha256'],'nativeDimensions':[card['source']['width'],card['source']['height']],'crop':card['recipe']['crop'],'construction':'Traditional GrabCut, manual polygon constraints and color cleanup; original RGB pixels preserved. No generated imagery.','guides':'artwork/masks/refinement-guides.json','rights':card['source']['rights']}
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><title>{html.escape(card["name"])} character alpha matte</title><metadata>{html.escape(json.dumps(metadata,ensure_ascii=False))}</metadata><path fill="white" fill-rule="evenodd" d="{" ".join(paths)}"/></svg>\n'
    local=f'artwork/masks/{identity}-character.svg'
    (root/local).write_text(svg)
    spec={'mask':{'localPath':local,'sha256':hashlib.sha256(svg.encode()).hexdigest(),'sourceSHA256':card['source']['sha256'],'crop':card['recipe']['crop']},'auraOpacity':.20,'cropEdgeFade':.025}
    card['recipe']['depthLayers']={d:json.loads(json.dumps(spec)) for d in card['designs']}
    if identity=='kenpachi-featured':
        card['recipe']['depthLayers']['crimson']=json.loads(json.dumps(spec))
        card['recipe']['depthLayers']['nocturne']['cropEdgeFade']=.10
        card['recipe']['depthLayers']['crimson']['cropEdgeFade']=.10
        card['recipe']['depthLayers']['crimson']['auraOpacity']=.10
    cut=original.copy();cut[:,:,3]=np.minimum(cut[:,:,3],alpha)
    bg=Image.new('RGBA',(w,h),'#263039');bg.alpha_composite(Image.fromarray(cut));bg.convert('RGB').save(preview/f'{identity}.jpg',quality=95)
    print(identity,len(paths),'contours')
cf.write_text(json.dumps(catalogue,indent=2,ensure_ascii=False)+'\n')
