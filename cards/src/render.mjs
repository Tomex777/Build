import fs from 'node:fs/promises';
import {chromium} from 'playwright';
import sharp from 'sharp';
import {inside} from './catalogue.mjs';
import {renderNoctisCard} from '../renderer/src/noctis.mjs';
import {renderRestoredCard} from '../renderer/src/restored.mjs';
import {characterCards} from '../renderer/src/characters.mjs';
import {powerPresentation} from './power.mjs';
import {composeDepthLayers} from './depth-layers.mjs';
import {frameDepth} from './frame-depth.mjs';
import {renderCrimson} from './crimson.mjs';
import {renderApex} from './apex.mjs';

export async function cardSvg(card,assets,design) {
  let subtitle=(card.formId==='featured'?card.name:card.name+' · '+card.form).toUpperCase();
  if(subtitle.length>40)subtitle=card.form.toUpperCase();
  const profile={id:card.character,name:card.title,subtitle,artwork:{id:card.id}};
  const depth=assets.depthLayers?.[design];
  let svg;
  if(['nocturne','tidelight'].includes(design)){
    const templates=JSON.parse(await fs.readFile(inside('renderer/examples/cards.json'),'utf8'));
    const def=characterCards(templates,profile).find(x=>x.theme===design);
    def.edition=(card.formId==='featured'?card.name:card.form.split(' / ')[0]).toUpperCase();
    def.number=String(card.number).padStart(3,'0');
    svg=renderRestoredCard(def,depth?{...assets,art:depth.foreground}:assets);
  }else if(['eclipse','ascendant'].includes(design))svg=renderApex(card,depth?{...assets,art:depth.foreground}:assets,design);
  else if(design==='crimson')svg=renderCrimson(card,depth?{...assets,art:depth.foreground}:assets);
  else svg=renderNoctisCard(profile,depth?{...assets,art:depth.foreground}:assets,{design,prefix:`library-${card.id}-${design}-`});
  const shift=card.recipe.horizontalShift?.[design];
  if(shift!==undefined){
    if(!Number.isFinite(shift)||Math.abs(shift)>500)throw Error('Invalid horizontal artwork adjustment');
    let count=0;
    svg=svg.replace(/<image\b([^>]*?)\bx="([^"]+)"/g,(_,before,x)=>{count++;return `<image${before}x="${Number(x)+shift}"`;});
    if(count!==1)throw Error('Expected one source illustration');
  }
  const placement=card.recipe.artworkPlacement?.[design];
  if(placement){
    const {x,y,width}=placement;
    if(![x,y,width].every(Number.isFinite)||Math.abs(x)>1000||Math.abs(y)>1000||width<200||width>2400)throw Error('Invalid artwork placement');
    let count=0;
    svg=svg.replace(/<image\b[^>]*\/>/g,image=>{count++;return image.replace(/\bx="[^"]*"/,`x="${x}"`).replace(/\by="[^"]*"/,`y="${y}"`).replace(/\bwidth="[^"]*"/,`width="${width}"`).replace(/\bheight="[^"]*"/,`height="${width*assets.artHeight/assets.artWidth}"`);});
    if(count!==1)throw Error('Expected one source illustration');
  }
  if(depth)svg=composeDepthLayers(svg,assets,depth,design);
  svg=frameDepth(svg,design);
  svg=powerPresentation(svg,card,design);
  return svg.replace('<svg ',`<svg data-catalogue-id="${card.id}" data-design="${design}" ${card.recipe.sourceMatte?`data-source-matte-sha256="${card.recipe.sourceMatte.sha256}" `:''}`);
}

export async function createSession() {
  const browser=await chromium.launch({headless:true,executablePath:process.env.NIGHT_BROWSER_PATH??inside('.cache/browser/chrome-headless-shell-linux64/chrome-headless-shell'),args:['--no-sandbox','--font-render-hinting=none']});
  if(browser.version()!=='131.0.6778.204'){await browser.close();throw Error('Use pinned Chrome for Testing 131.0.6778.204 for reproducible exports');}
  return {close:()=>browser.close(),async render(svg,width,focal){
    if(!Number.isInteger(width)||width<320||width>4000)throw Error('Render width must be 320–4000 pixels');
    const context=await browser.newContext({viewport:{width:1000,height:1600},deviceScaleFactor:width/1000});
    try{
      const page=await context.newPage();page.setDefaultTimeout(120000);
      await page.route('**/*',route=>route.request().url().startsWith('data:')?route.continue():route.abort());
      await page.setContent('<!doctype html><style>html,body{margin:0;background:transparent}svg{display:block}</style>'+svg,{timeout:120000});
      await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.querySelectorAll('image')].map(async n=>{const i=new Image();i.src=n.getAttribute('href');await i.decode();}));});
      const qa=await page.evaluate(async(focal)=>{
        const root=document.querySelector('svg'),ids=[...root.querySelectorAll('[id]')].map(n=>n.id),set=new Set(ids);
        if(set.size!==ids.length)throw Error('Duplicate SVG identifiers');
        for(const n of root.querySelectorAll('*'))for(const a of n.attributes){for(const m of a.value.matchAll(/url\(#([^)]*)\)/g))if(!set.has(m[1]))throw Error('Unresolved SVG paint reference');if(a.name==='href'&&a.value.startsWith('#')&&!set.has(a.value.slice(1)))throw Error('Unresolved SVG reference');}
        const names=[...root.querySelectorAll('[data-name],[data-text="name"]')];
        if(!names.length)throw Error('Missing character title');
        const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');
        for(const n of names){
          const st=getComputedStyle(n);ctx.font=`${st.fontStyle} ${st.fontWeight} ${st.fontSize} ${st.fontFamily}`;
          const m=ctx.measureText(n.textContent),start=n.getStartPositionOfChar(0),spacing=parseFloat(st.letterSpacing)||0;
          const bbox={x:start.x-m.actualBoundingBoxLeft,y:start.y-m.actualBoundingBoxAscent,w:m.actualBoundingBoxLeft+m.actualBoundingBoxRight+Math.max(0,n.textContent.length-1)*spacing,h:m.actualBoundingBoxAscent+m.actualBoundingBoxDescent};
          for(const [x,y] of [[bbox.x,bbox.y],[bbox.x+bbox.w,bbox.y],[bbox.x,bbox.y+bbox.h],[bbox.x+bbox.w,bbox.y+bbox.h]]){
            const p=new DOMPoint(x,y).matrixTransform(n.getScreenCTM());if(p.x<1||p.x>999||p.y<1||p.y>1599)throw Error('Character title extends outside card');
          }
        }
        if(root.dataset.design==='nocturne'&&[...root.querySelectorAll('ellipse')].some(n=>!n.closest('defs')))throw Error('Nocturne circular backdrop returned');
        const rankLayer=root.querySelector('[data-layer="power-rank"]'),rankText=root.querySelector('[data-rank-source]'),rankInk=rankLayer?.querySelector('[data-rank-ink]');
        if(!rankInk||!['F','E','D','C','B','A','S','SS','SSS','EX','—'].includes(rankText?.textContent))throw Error('Missing or invalid glass rank lettering');
        if(rankLayer.querySelector('rect')||rankLayer.dataset.powerMaterial!=='glass-letters')throw Error('Rank lettering must have no background plate');
        const rankStyle=getComputedStyle(rankText);ctx.font=`${rankStyle.fontStyle} ${rankStyle.fontWeight} ${rankStyle.fontSize} ${rankStyle.fontFamily}`;
        const rankMetrics=ctx.measureText(rankText.textContent),rankStart=rankText.getStartPositionOfChar(0),rankSpacing=parseFloat(rankStyle.letterSpacing)||0;
        const gradeBox={left:rankStart.x-rankMetrics.actualBoundingBoxLeft,right:rankStart.x+rankMetrics.actualBoundingBoxRight+Math.max(0,rankText.textContent.length-1)*rankSpacing,top:rankStart.y-rankMetrics.actualBoundingBoxAscent,bottom:rankStart.y+rankMetrics.actualBoundingBoxDescent};
        const area={x:Number(rankLayer.dataset.rankX),y:Number(rankLayer.dataset.rankY),w:Number(rankLayer.dataset.rankWidth),h:Number(rankLayer.dataset.rankHeight)};
        if(gradeBox.left<area.x||gradeBox.right>area.x+area.w||gradeBox.top<area.y||gradeBox.bottom>area.y+area.h)throw Error('Glass rank lettering overflows its reserved corner');
        const image=root.querySelector('image'),use=[...root.querySelectorAll('use')].find(n=>n.getAttribute('href')==='#'+image?.id&&!n.closest('defs')&&!n.closest('[data-layer="power-rank"]'));
        if(!image||!use)throw Error('Missing embedded illustration');
        const decoded=new Image();decoded.src=image.getAttribute('href');await decoded.decode();
        const scale=Math.min(image.width.baseVal.value/decoded.naturalWidth,image.height.baseVal.value/decoded.naturalHeight);
        const x=image.x.baseVal.value+(image.width.baseVal.value-decoded.naturalWidth*scale)/2+focal[0]*decoded.naturalWidth*scale;
        const y=image.y.baseVal.value+(image.height.baseVal.value-decoded.naturalHeight*scale)/2+focal[1]*decoded.naturalHeight*scale;
        const own=image.transform.baseVal.consolidate()?.matrix;
        const matrix=own?use.getScreenCTM().multiply(own):use.getScreenCTM();
        const p=new DOMPoint(x,y).matrixTransform(matrix);
        if(p.x<75||p.x>925||p.y<100||p.y>1150)throw Error(`Subject focal point outside safe area: ${Math.round(p.x)},${Math.round(p.y)}`);
        const boundary=root.querySelector('[data-frame-boundary]');
        if(!boundary||root.dataset.characterBreakout!=='required')throw Error('Missing character breakout boundary');
        const figure=document.createElement('canvas');figure.width=1000;figure.height=1600;
        const fc=figure.getContext('2d',{willReadFrequently:true});
        fc.setTransform(matrix.a,matrix.b,matrix.c,matrix.d,matrix.e,matrix.f);
        const fw=decoded.naturalWidth*scale,fh=decoded.naturalHeight*scale;
        fc.drawImage(decoded,image.x.baseVal.value+(image.width.baseVal.value-fw)/2,image.y.baseVal.value+(image.height.baseVal.value-fh)/2,fw,fh);
        const pixels=fc.getImageData(0,0,1000,1600).data;
        const alpha=(x,y)=>{x=Math.round(x);y=Math.round(y);return x>35&&x<965&&y>90&&y<1150?pixels[(y*1000+x)*4+3]:0;};
        const bm=boundary.getScreenCTM(),length=boundary.getTotalLength();
        const point=t=>{const v=boundary.getPointAtLength(Math.max(0,Math.min(length,t)));return new DOMPoint(v.x,v.y).matrixTransform(bm);};
        let crossing=0,run=0,maxRun=0;
        for(let t=0;t<length;t+=3){
          const a=point(t),b=point(t+2),dx=b.x-a.x,dy=b.y-a.y,n=Math.hypot(dx,dy)||1;
          if(alpha(a.x-dy/n*8,a.y+dx/n*8)>180&&alpha(a.x+dy/n*8,a.y-dx/n*8)>180){crossing+=3;run+=3;maxRun=Math.max(maxRun,run);}else run=0;
        }
        if(maxRun<24)throw Error(`Character does not visibly cross the inner frame (longest crossing ${maxRun}px)`);
        return {artworkDecoded:true,uniqueIDs:true,validReferences:true,nameInkWithinCard:true,powerRank:rankText.textContent,powerUniverse:root.dataset.powerUniverse,powerGlyphWithinBounds:true,powerBackgroundAbsent:true,nameScale:Number(names[0].dataset.titleScale),circleAbsent:root.dataset.design==='nocturne'?true:null,focalPosition:[Math.round(p.x),Math.round(p.y)],characterBreakout:{required:true,crossingLength:crossing,longestCrossing:maxRun,foregroundMask:root.dataset.foregroundMaskSha256??root.dataset.sourceMatteSha256??'source-alpha'}};
      },focal);
      const png=await page.locator('svg').screenshot({omitBackground:true,timeout:120000});
      const m=await sharp(png).metadata();if(m.width!==width||m.height!==width*1.6)throw Error('Incorrect export dimensions');
      await sharp(png).raw().toBuffer();
      return {png,qa};
    }finally{await context.close();}
  }};
}
