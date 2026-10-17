import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import sharp from 'sharp';
import {ROOT} from '../src/node-assets.mjs';
import {launch} from './browser.mjs';
const browser=await launch();const errors=[],requests=[],checks=[];
const q=ROOT+'/qa/depth-final';await fs.mkdir(q,{recursive:true});
const page=await browser.newPage({viewport:{width:1440,height:1100},acceptDownloads:true});
page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))requests.push(r.url());});
try{
 await page.context().setOffline(true);
 await page.goto(pathToFileURL(ROOT+'/dist/NIGHT-Depth-Editions.html').href);await page.evaluate(()=>document.fonts.ready);
 for(const edition of ['nocturne','tidelight']){
  await page.locator(`[data-card="naruto-${edition}"]`).click();
  assert.equal(await page.locator('#face [data-text="name"]').textContent(),'NARUTO');
  assert.equal(await page.locator('#face [data-depth]').count(),3);
  // The live layer positions, light gradients and cast shadows must match a fresh render.
  for(const light of [[.12,.16],[.88,.79]]){
   const parity=await page.evaluate(l=>{
    window.NIGHT_STUDIO.illuminate(...l);
    const state=window.NIGHT_STUDIO.getState(),{cards,assets}=JSON.parse(document.getElementById('card-data').textContent);
    const c=cards[state.index],scratch=document.createElement('div');scratch.innerHTML=window.Night.renderCard(c,assets[c.id],{light:l});
    const live=document.querySelector('#face svg'),fresh=scratch.querySelector('svg');
    const attrs=(r)=>[...r.querySelectorAll('[data-depth], [id$="cast"] feDropShadow, [id$="metal"], [id$="lensLight"]')].map(n=>[...n.attributes].map(a=>[a.name,a.value]));
    return JSON.stringify(attrs(live))===JSON.stringify(attrs(fresh));
   },light);
   assert.ok(parity,`${edition}: live and export lighting differ`);
  }
  await page.locator('#stage').focus();await page.keyboard.press('Home');
  await page.locator('#finish').click();assert.equal(await page.locator('#finish').getAttribute('aria-pressed'),'false');await page.locator('#finish').click();
  await page.locator('#turn').click();assert.ok((await page.locator('#face').textContent()).includes('Artwork rights reserved'));await page.locator('#turn').click();
  checks.push(`${edition}: layered artwork, lighting/export parity, foil control, reverse`);
 }
 const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#png-export').click()]);
 await download.saveAs(q+'/browser-export.png');const m=await sharp(q+'/browser-export.png').metadata();assert.equal(m.width,3000);assert.equal(m.height,4200);checks.push('4K browser export');
 await page.screenshot({path:q+'/studio-desktop.png'});
 await page.setViewportSize({width:390,height:900});
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:q+'/studio-mobile.png',fullPage:true});checks.push('390px touch layout without overflow');
 await page.emulateMedia({reducedMotion:'reduce'});await page.reload();await page.evaluate(()=>document.fonts.ready);
 const reduced=await page.evaluate(()=>{const n=document.querySelector('[data-depth="subject"]'),before=n.getAttribute('transform');window.NIGHT_STUDIO.illuminate(.9,.9);return before===n.getAttribute('transform');});assert.ok(reduced);checks.push('Reduced-motion preference disables parallax');
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 await fs.writeFile(q+'/browser-verification.json',JSON.stringify({checks,errors,externalRequests:requests},null,2));console.log(JSON.stringify({checks,errors,externalRequests:requests}));
}finally{await browser.close();}
