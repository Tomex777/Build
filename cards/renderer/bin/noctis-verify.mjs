/** Verify the actual SVG DOM and portable gallery in Chromium, including the
 * duplicate paint-server IDs that caused a missing title in the first pass. */
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {launch} from './browser.mjs';
import {renderNoctisCard,NOCTIS_DESIGNS} from '../src/noctis.mjs';
import {loadNoctisAssets} from '../src/noctis-assets.mjs';
const root=path.resolve(import.meta.dirname,'..'),out=path.join(root,'qa/noctis-final');
await fs.mkdir(out,{recursive:true});
const browser=await launch(),report={profiles:[],gallery:{},preservation:{}};
try{
 const page=await browser.newPage({viewport:{width:1100,height:1700}});
 for(const id of ['ichigo','naruto']){
  const c=JSON.parse(await fs.readFile(path.join(root,'examples/characters',id+'.json'),'utf8')),a=await loadNoctisAssets(c);
  for(const d of NOCTIS_DESIGNS){
   const svg=renderNoctisCard(c,a,{design:d.id});
   await page.setContent(`<style>body{margin:0}svg{display:block}</style>${svg}`);
   await page.evaluate(()=>document.fonts.ready);
   const r=await page.evaluate(()=>{
    const svg=document.querySelector('svg'),ids=[...svg.querySelectorAll('[id]')].map(e=>e.id),duplicates=ids.filter((id,i)=>ids.indexOf(id)!==i);
    const missing=[...svg.outerHTML.matchAll(/url\(#([^\)]+)\)/g)].map(m=>m[1]).filter(id=>!document.getElementById(id));
    const names=[...svg.querySelectorAll('[data-text],[data-name]')].map(t=>{const r=t.getBoundingClientRect(),style=getComputedStyle(t),fill=style.fill;
     const ctx=document.createElement('canvas').getContext('2d');ctx.font=`${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
     const m=ctx.measureText(t.textContent),advance=t.getComputedTextLength(),spacing=advance-m.width;
     const origin=+t.getAttribute('x')-(style.textAnchor==='middle'?advance/2:style.textAnchor==='end'?advance:0),baseline=+t.getAttribute('y');
     const left=origin-m.actualBoundingBoxLeft,right=origin+m.actualBoundingBoxRight+Math.max(0,spacing),top=baseline-m.actualBoundingBoxAscent,bottom=baseline+m.actualBoundingBoxDescent;
     const matrix=t.getScreenCTM(),points=[[left,top],[right,top],[left,bottom],[right,bottom]].map(([x,y])=>new DOMPoint(x,y).matrixTransform(matrix));
     const x=Math.min(...points.map(p=>p.x)),y=Math.min(...points.map(p=>p.y)),w=Math.max(...points.map(p=>p.x))-x,h=Math.max(...points.map(p=>p.y))-y;
     const match=fill.match(/#([^\)"']+)/),target=match?document.getElementById(match[1]):null;
     return {text:t.textContent,bounds:[x,y,w,h],layoutBounds:[r.x,r.y,r.width,r.height],fill,paintServer:target?.tagName??null}});
    const images=[...svg.querySelectorAll('image')].map(i=>{const w=+i.getAttribute('width'),h=+i.getAttribute('height');return {width:w,height:h,preserve:i.getAttribute('preserveAspectRatio')}});
    return {viewBox:svg.getAttribute('viewBox'),duplicates,missing,names,images};
   });
   assert.equal(r.viewBox,'0 0 1000 1600');assert.deepEqual(r.duplicates,[]);assert.deepEqual(r.missing,[]);assert.equal(r.names.length,1);
   for(const t of r.names){const [x,y,w,h]=t.bounds;assert.ok(x>=0&&y>=0&&x+w<=1001&&y+h<=1600,`${id} ${d.id}: title exceeds card`);assert.ok(w>100&&h>30);if(t.fill.startsWith('url'))assert.ok(['linearGradient','radialGradient','pattern'].includes(t.paintServer),'Title references a non-paint element');}
   for(const i of r.images)assert.ok(Math.abs(i.width/i.height-a.artWidth/a.artHeight)<.0001,'Artwork is stretched');
   report.profiles.push({character:id,design:d.id,...r});
  }
 }
 await page.close();
 const desktop=await browser.newPage({viewport:{width:1800,height:1500}}),errors=[];
 desktop.on('pageerror',e=>errors.push(e.message));
 await desktop.goto(pathToFileURL(path.join(root,'dist/NIGHT-Noctis-Collection.html')).href);
 await desktop.locator('article').last().waitFor();await desktop.evaluate(()=>document.fonts.ready);
 assert.equal(await desktop.locator('article').count(),20);
 await desktop.getByRole('button',{name:'View 03 Prism',exact:true}).click();
 await desktop.getByRole('heading',{name:'Prism',exact:true}).waitFor();
 await desktop.locator('.save').click();assert.equal(await desktop.locator('#count').textContent(),'1 saved');
 const download=await desktop.locator('.download').getAttribute('download');assert.equal(download,'03-prism-ichigo.svg');
 await desktop.locator('.next').click();assert.equal(await desktop.locator('#detail-name').textContent(),'Vanguard');
 await desktop.keyboard.press('ArrowLeft');assert.equal(await desktop.locator('#detail-name').textContent(),'Prism');
 await desktop.keyboard.press('Escape');
 await desktop.locator('.filter').click();assert.equal(await desktop.locator('article:visible').count(),1);
 await desktop.reload();await desktop.locator('article').last().waitFor();assert.equal(await desktop.locator('#count').textContent(),'1 saved');
 await desktop.screenshot({path:path.join(out,'gallery-desktop.png'),fullPage:true});
 report.gallery={cards:20,savedPicksPersist:true,enlargement:true,keyboardNavigation:true,svgDownload:true,pageErrors:errors};assert.deepEqual(errors,[]);
 const mobile=await browser.newPage({viewport:{width:390,height:844},isMobile:true,deviceScaleFactor:1,hasTouch:true});
 await mobile.goto(pathToFileURL(path.join(root,'dist/NIGHT-Noctis-Collection.html')).href);await mobile.locator('article').last().waitFor();await mobile.evaluate(()=>document.fonts.ready);
 assert.ok(await mobile.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile gallery overflows');
 await mobile.getByRole('button',{name:'View 20 Ivory',exact:true}).click();await mobile.locator('dialog[open]').waitFor();
 await mobile.screenshot({path:path.join(out,'gallery-phone.png')});
 report.gallery.mobileWidth=390;report.gallery.mobileOverflow=false;
 const baseline=JSON.parse(await fs.readFile(path.join(root,'qa/noctis-preserved-baseline.json'),'utf8'));
 for(const [file,sha]of Object.entries(baseline))assert.equal(crypto.createHash('sha256').update(await fs.readFile(path.join(root,file))).digest('hex'),sha,`Preserved file changed: ${file}`);
 const sha=f=>fs.readFile(f).then(b=>crypto.createHash('sha256').update(b).digest('hex'));
 assert.equal(await sha(path.join(root,'preserved/obsidian-v1/01-obsidian-ichigo.png')),'d20d9d074cd8d5835dc99502314500c7da6ecb08c1ee5605dcf5460ad7fd7156');
 assert.equal(await sha(path.join(root,'dist/noctis/01-obsidian-ichigo.png')),await sha(path.join(root,'preserved/obsidian-v1/01-obsidian-ichigo.png')));
 report.preservation={files:Object.keys(baseline).length,allUnchanged:true,obsidianByteIdentical:true};
 await fs.writeFile(path.join(out,'verification.json'),JSON.stringify(report,null,2));
 console.log(`Verified 40 character/design combinations; all paint references, titles and art proportions; desktop/mobile gallery; all ${report.preservation.files} preserved files unchanged.`);
}finally{await browser.close()}
