/** Verify the actual SVG DOM and portable gallery in Chromium, including the
 * duplicate paint-server IDs that caused a missing title in the first pass. */
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {launch} from './browser.mjs';
import {renderAtelierCard,ATELIER_DESIGNS} from '../src/atelier.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
const root=path.resolve(import.meta.dirname,'..'),out=path.join(root,'qa/atelier-final');
await fs.mkdir(out,{recursive:true});
const browser=await launch(),report={profiles:[],gallery:{},preservation:{}};
try{
 const page=await browser.newPage({viewport:{width:1100,height:1700}});
 for(const id of ['ichigo','naruto']){
  const c=JSON.parse(await fs.readFile(path.join(root,'examples/characters',id+'.json'),'utf8')),a=await loadCharacterAssets(c);
  for(const d of ATELIER_DESIGNS){
   const svg=renderAtelierCard(c,a,{design:d.id});
   await page.setContent(`<style>body{margin:0}svg{display:block}</style>${svg}`);
   await page.evaluate(()=>document.fonts.ready);
   const r=await page.evaluate(()=>{
    const svg=document.querySelector('svg'),ids=[...svg.querySelectorAll('[id]')].map(e=>e.id),duplicates=ids.filter((id,i)=>ids.indexOf(id)!==i);
    const missing=[...svg.outerHTML.matchAll(/url\(#([^\)]+)\)/g)].map(m=>m[1]).filter(id=>!document.getElementById(id));
    const names=[...svg.querySelectorAll('[data-text]')].map(t=>{const r=t.getBoundingClientRect(),fill=getComputedStyle(t).fill;const match=fill.match(/#([^\)"']+)/);const target=match?document.getElementById(match[1]):null;return {text:t.textContent,bounds:[r.x,r.y,r.width,r.height],fill,paintServer:target?.tagName??null}});
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
 await desktop.goto(pathToFileURL(path.join(root,'dist/NIGHT-Ten-Designs.html')).href);
 await desktop.locator('article').last().waitFor();await desktop.evaluate(()=>document.fonts.ready);
 assert.equal(await desktop.locator('article').count(),10);
 await desktop.getByRole('button',{name:'View 03 Spectra',exact:true}).click();
 await desktop.getByRole('heading',{name:'Spectra',exact:true}).waitFor();
 await desktop.locator('.save').click();assert.equal(await desktop.locator('#count').textContent(),'1 saved');
 const download=await desktop.locator('.download').getAttribute('download');assert.equal(download,'03-spectra-ichigo.svg');
 await desktop.locator('.next').click();assert.equal(await desktop.locator('#detail-name').textContent(),'Monograph');
 await desktop.keyboard.press('ArrowLeft');assert.equal(await desktop.locator('#detail-name').textContent(),'Spectra');
 await desktop.keyboard.press('Escape');
 await desktop.locator('.filter').click();assert.equal(await desktop.locator('article:visible').count(),1);
 await desktop.reload();await desktop.locator('article').last().waitFor();assert.equal(await desktop.locator('#count').textContent(),'1 saved');
 await desktop.screenshot({path:path.join(out,'gallery-desktop.png'),fullPage:true});
 report.gallery={cards:10,savedPicksPersist:true,enlargement:true,keyboardNavigation:true,svgDownload:true,pageErrors:errors};assert.deepEqual(errors,[]);
 const mobile=await browser.newPage({viewport:{width:390,height:844},isMobile:true,deviceScaleFactor:1,hasTouch:true});
 await mobile.goto(pathToFileURL(path.join(root,'dist/NIGHT-Ten-Designs.html')).href);await mobile.locator('article').last().waitFor();await mobile.evaluate(()=>document.fonts.ready);
 assert.ok(await mobile.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile gallery overflows');
 await mobile.getByRole('button',{name:'View 10 Stratum',exact:true}).click();await mobile.locator('dialog[open]').waitFor();
 await mobile.screenshot({path:path.join(out,'gallery-phone.png')});
 report.gallery.mobileWidth=390;report.gallery.mobileOverflow=false;
 const baseline=JSON.parse(await fs.readFile(path.join(root,'qa/atelier-preserved-baseline.json'),'utf8'));
 for(const [file,sha]of Object.entries(baseline))assert.equal(crypto.createHash('sha256').update(await fs.readFile(path.join(root,file))).digest('hex'),sha,`Preserved file changed: ${file}`);
 report.preservation={files:Object.keys(baseline).length,allUnchanged:true};
 await fs.writeFile(path.join(out,'verification.json'),JSON.stringify(report,null,2));
 console.log(`Verified 20 character/design combinations; all paint references, titles and art proportions; desktop/mobile gallery; all ${report.preservation.files} preserved files unchanged.`);
}finally{await browser.close()}
