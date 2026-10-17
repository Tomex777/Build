import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import sharp from 'sharp';
import {ROOT} from '../src/node-assets.mjs';
import {launch} from './browser.mjs';

const profile=JSON.parse(await fs.readFile(path.resolve(process.argv[2]??path.join(ROOT,'examples/characters/naruto.json')),'utf8'));
const out=path.resolve(process.argv[3]??path.join(ROOT,'dist',profile.id));
const width=Number(process.argv[4]??3000),height=Math.round(width*1.4);
const qa=path.join(ROOT,'qa',profile.id);
await fs.mkdir(qa,{recursive:true});
const browser=await launch(),errors=[],external=[];
const page=await browser.newPage({viewport:{width:1440,height:1100},acceptDownloads:true});
page.on('pageerror',e=>errors.push(e.message));
page.on('request',r=>{if(/^https?:/.test(r.url()))external.push(r.url());});
try{
 await page.context().setOffline(true);
 await page.goto(pathToFileURL(path.join(out,`NIGHT-${profile.id}.html`)).href);
 await page.evaluate(()=>document.fonts.ready);
 for(const edition of ['nocturne','tidelight','verdant']){
  await page.locator(`[data-card="${profile.id}-${edition}"]`).click();
  const title=await page.locator('#face svg title').textContent();
  assert.equal(title,`${profile.name} — ${edition.toUpperCase()} (front)`);
  assert.equal(await page.locator('#face [data-text="name"]').textContent(),profile.name);
  const metadata=await sharp(path.join(out,`NIGHT-${profile.id}-${edition}-${width}.png`)).metadata();
  assert.equal(metadata.width,width);assert.equal(metadata.height,height);
 }
 const before=await page.locator('#face svg linearGradient').first().getAttribute('x1');
 await page.locator('#stage').focus();await page.keyboard.press('ArrowRight');
 const after=await page.locator('#face svg linearGradient').first().getAttribute('x1');
 assert.notEqual(before,after,'Live foil lighting must respond');
 await page.keyboard.press('Home');
 const [download]=await Promise.all([page.waitForEvent('download'),page.locator('#png-export').click()]);
 assert.equal(download.suggestedFilename(),`NIGHT-${profile.id}-verdant-front-${width}.png`);
 const exportPath=path.join(qa,'browser-export.png');await download.saveAs(exportPath);
 const meta=await sharp(exportPath).metadata();assert.equal(meta.width,width);assert.equal(meta.height,height);
 await page.locator('#turn').click();
 assert.equal(await page.locator('#face svg title').textContent(),`${profile.name} — VERDANT (back)`);
 assert.ok((await page.locator('#face').textContent()).includes(profile.artwork.outputLicense));
 await page.locator('#turn').click();
 await page.screenshot({path:path.join(qa,'studio-desktop.png')});
 await page.setViewportSize({width:390,height:900});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.screenshot({path:path.join(qa,'studio-mobile.png'),fullPage:true});
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 const result={character:profile.id,threeEditions:true,fullResolution:[width,height],offline:true,liveFoil:true,reverse:true,export:true,mobileOverflow:false,errors,externalRequests:external};
 await fs.writeFile(path.join(qa,'studio-verification.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify(result));
}finally{await browser.close();}
