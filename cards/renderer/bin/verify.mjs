import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import sharp from 'sharp';
import {ROOT} from '../src/node-assets.mjs';
import {launch} from './browser.mjs';
const out=path.join(ROOT,'qa');await fs.mkdir(out,{recursive:true});
const url=pathToFileURL(path.join(ROOT,'dist/index.html')).href;
const browser=await launch(),errors=[],external=[];
const report={started:new Date().toISOString(),browser:browser.version(),checks:[],limitations:['WhatsApp sizes tested as rendered images, without sending through WhatsApp.','Desktop Chromium and touch emulation; no physical Android device.','Foil is a digital light simulation, not an optically variable print finish.']};
const check=(name,value=true)=>{assert(value,name);report.checks.push(name)};
try{
 const page=await browser.newPage({viewport:{width:1440,height:1060},deviceScaleFactor:1,acceptDownloads:true});
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))external.push(r.url())});
 await page.goto(url);await page.evaluate(()=>document.fonts.ready);
 check('Offline studio mounts a card',await page.locator('#face svg').count()===1);
 await page.screenshot({path:path.join(out,'studio-desktop.png'),fullPage:true});
 for(const id of ['nocturne','tidelight','verdant']){
  await page.locator(`[data-card="${id}"]`).click();
  await page.locator('#stage').focus();await page.keyboard.press('Home');
  await page.evaluate(()=>document.getElementById('face').style.transform='none');
  for(const side of ['front','back']){
   if(side==='back')await page.locator('#turn').click();
   const violations=await page.locator('#face svg').evaluate(svg=>[...svg.querySelectorAll('text')].map(t=>{const b=t.getBBox();return {text:t.textContent,x:b.x,y:b.y,right:b.x+b.width,bottom:b.y+b.height}}).filter(b=>b.x<45||b.right>955||b.y<45||b.bottom>1355));
   check(`${id} ${side}: all typography inside safe margins`,violations.length===0);
   const healthy=await page.locator('#face svg').evaluate(s=>{
    const parsed=new DOMParser().parseFromString(new XMLSerializer().serializeToString(s),'image/svg+xml');
    const invalid=[...s.querySelectorAll('*')].flatMap(e=>[...e.attributes]).some(a=>!a.value.startsWith('data:')&&/\b(?:NaN|Infinity)\b/.test(a.value));
    return !parsed.querySelector('parsererror')&&!s.querySelector('script')&&!invalid;
   });
   check(`${id} ${side}: valid self-contained SVG`,healthy);
  }
 }
 // Compare initial SVG light uniforms with the live pointer/keyboard path.
 for(const pose of [[.05,.08],[.28,.2],[.90,.85]]){
  const parity=await page.evaluate(pose=>{
   const data=JSON.parse(document.getElementById('card-data').textContent),state=window.NIGHT_STUDIO.getState();
   const card=data.cards[state.index],expected=new DOMParser().parseFromString(window.Night.renderCard(card,data.assets[card.id],{side:state.side,light:pose,foil:state.foil?1:0}),'image/svg+xml');
   window.NIGHT_STUDIO.illuminate(...pose);
   const actual=document.querySelector('#face svg');
   return ['metal','foil','stripe','spec'].every(kind=>{
    const a=actual.querySelector(`[id$="${kind}"]`),b=expected.querySelector(`[id$="${kind}"]`);
    return ['x1','x2','y1','y2','cx','cy','gradientUnits'].every(k=>a.getAttribute(k)===b.getAttribute(k));
   });
  },pose);
  check(`Live and exported lighting match at ${pose.join(',')}`,parity);
 }
 await page.locator('[data-card="nocturne"]').click();
 await page.locator('#stage').focus();await page.keyboard.press('ArrowRight');
 check('Keyboard moves the foil light',await page.evaluate(()=>window.NIGHT_STUDIO.getState().light[0])>.28);
 await page.locator('#finish').click();check('Foil can be disabled',await page.locator('#finish').getAttribute('aria-pressed')==='false');
 await page.locator('#finish').click();
 await page.locator('#stage').focus();await page.keyboard.press('Home');
 await page.evaluate(()=>document.getElementById('face').style.transform='none');
 const [svgDownload]=await Promise.all([page.waitForEvent('download'),page.locator('#svg-export').click()]);
 await svgDownload.saveAs(path.join(out,'browser-export.svg'));
 check('SVG export preserves artwork and attribution',(await fs.readFile(path.join(out,'browser-export.svg'),'utf8')).includes('Tyson Tan'));
 const [pngDownload]=await Promise.all([page.waitForEvent('download'),page.locator('#png-export').click()]);
 await pngDownload.saveAs(path.join(out,'browser-export.png'));
 const png=await sharp(path.join(out,'browser-export.png')).metadata();check('Browser PNG export is 2000 × 2800',png.width===2000&&png.height===2800);
 const stats=await sharp(path.join(out,'browser-export.png')).stats();check('Browser export contains detailed artwork',stats.channels[0].stdev>35);
 const raceDownload=page.waitForEvent('download');
 await page.evaluate(()=>{document.getElementById('png-export').click();document.querySelector('[data-card="verdant"]').click();});
 const raced=await raceDownload;
 check('An in-progress PNG export retains its starting card and side',raced.suggestedFilename()==='NIGHT-nocturne-front-2000.png');
 await raced.saveAs(path.join(out,'browser-export-during-selection.png'));

 for(const width of [360,390]){
  const ctx=await browser.newContext({viewport:{width,height:900},deviceScaleFactor:1,isMobile:true,hasTouch:true});
  const mobile=await ctx.newPage();mobile.on('pageerror',e=>errors.push(e.message));await mobile.goto(url);await mobile.evaluate(()=>document.fonts.ready);
  check(`${width}px touch layout has no horizontal overflow`,await mobile.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await mobile.screenshot({path:path.join(out,`studio-mobile-${width}.png`),fullPage:true});
  for(const id of ['tidelight','verdant']){
   await mobile.locator(`[data-card="${id}"]`).tap();check(`${width}px touch selection works for ${id}`,await mobile.locator('#face title').textContent()===`Kiki — ${id.toUpperCase()} (front)`);
  }
  await ctx.close();
 }
 const reduced=await browser.newPage({reducedMotion:'reduce'});await reduced.goto(url);check('Reduced motion removes tilt animation',await reduced.locator('#face').evaluate(e=>getComputedStyle(e).transitionDuration)==='0s');await reduced.close();
 for(const id of ['nocturne','tidelight','verdant']){
  for(const width of [320,360]){
   const m=await sharp(path.join(ROOT,`dist/${id}-whatsapp-${width}.png`)).metadata();check(`${id} native ${width}px export has correct dimensions`,m.width===width&&m.height===Math.round(width*1.4));
  }
  const m=await sharp(path.join(ROOT,`dist/${id}-front-2000.png`)).metadata();check(`${id} full export is 2000 × 2800`,m.width===2000&&m.height===2800);
  const jpeg=await sharp(path.join(ROOT,`dist/${id}-whatsapp-1600.jpg`)).metadata();check(`${id} sharing JPEG has 1600px long edge`,jpeg.height===1600);
 }
 check('Studio works without external requests',external.length===0);check('No browser JavaScript errors',errors.length===0);
 report.completed=new Date().toISOString();report.passed=report.checks.length;
 await fs.writeFile(path.join(out,'verification.json'),JSON.stringify(report,null,2));
 console.log(`${report.passed} browser and export checks passed.`);
}finally{await browser.close()}
