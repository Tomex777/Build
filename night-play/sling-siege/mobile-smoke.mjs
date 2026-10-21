// Sling Siege: full source page, real Chromium input, score and 100-stage route regression.
// Run: node night-play/sling-siege/mobile-smoke.mjs (npm i --no-save playwright@1.56.1)
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';
const home=dirname(fileURLToPath(import.meta.url));
const gameUrl=pathToFileURL(resolve(home,'index.html')).href;
const evidence=resolve(home,'visual-output');
await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const widths=[320,360,390,412,1280], results=[];
try{
 for(const width of widths){
  const page=await browser.newPage({viewport:{width,height:width===1280?950:844},hasTouch:true,isMobile:width!==1280});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(gameUrl);
  assert.equal(await page.locator('#overlay').isVisible(),true,'start overlay');
  assert.equal((await page.evaluate(()=>window.__slingTest.state)).levelCount,100,'100 levels are available');
  await page.locator('#overlayAction').click();
  assert.equal((await page.evaluate(()=>window.__slingTest.state)).gameState,'ready');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  assert.ok(overflow<=1,'no horizontal overflow at '+width+': '+overflow+'px');
  await page.screenshot({path:resolve(evidence,'sling-start-'+width+'.png'),fullPage:true});
  const box=await page.locator('#game').boundingBox();
  assert.ok(box?.height>=215,'canvas should be touch-sized at '+width);
  if(width===320||width===390){
   // Native CDP touch stream, not a JS function simulating a touch.
   const cd=await page.context().newCDPSession(page);
   const xScale=box.width/720,skyPad=box.height-box.width*540/720;
   const fx=box.x+140*xScale,fy=box.y+skyPad+362*xScale;
   await cd.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:fx,y:fy}]});
   await cd.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:fx-32,y:fy+36}]});
   assert.equal(await page.locator('#board').evaluate(e=>e.classList.contains('aiming')),true,'real touch pulls sling');
   await cd.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   assert.equal((await page.evaluate(()=>window.__slingTest.state)).used,1,'real touch released a shot');
   await page.screenshot({path:resolve(evidence,'sling-touch-'+width+'.png')});
  }
  if(width===390){
   // Grounding regression: after supports are destroyed, roof must drop to the
   // floor even when the projectile is no longer flying. This previously froze.
   const unsupported=await page.evaluate(()=>{
    const t=window.__slingTest;t.reset();
    const before=t.state.blocks[2].y;
    t.dislodgeSupportsForTest();t.step(240);
    return {before,after:t.state.blocks[2].y};
   });
   assert.ok(unsupported.after>unsupported.before+10,
    'roof must fall under gravity after its supports disappear: '+JSON.stringify(unsupported));
   // Round 1 genuine collisions and victory + 3-star/localStorage persistence.
   const first=await page.evaluate(()=>{const a=window.__slingTest;a.reset();a.fireAt(66,-65);a.step(1150);return a.state});
   assert.equal(first.gameState,'won','first stage must be physically winnable');
   assert.equal(first.enemies.filter(e=>e.alive).length,0,'all stage 1 enemies destroyed');
   assert.ok(first.score>=200,'earned score (not mock)');
   assert.ok(Number(await page.locator('#best').textContent())>=first.score,'best recorded');
   assert.ok((await page.locator('#stars').textContent()).includes('★'),'stars awarded');
   await page.screenshot({path:resolve(evidence,'sling-win-390.png')});
   await page.locator('#overlayAction').click();
   assert.equal((await page.evaluate(()=>window.__slingTest.state)).levelIdx,1,'next stage loads');
   await page.locator('#overlayAction').click();
   const miss=await page.evaluate(()=>{const a=window.__slingTest;a.reset();for(let i=0;i<4;i++){a.fireAt(24,-14);a.step(1200)}return a.state});
   assert.equal(miss.gameState,'lost','running out of shots must produce defeat');
   await page.screenshot({path:resolve(evidence,'sling-loss-390.png')});
   await page.locator('#overlayAction').click();
   assert.equal((await page.evaluate(()=>window.__slingTest.state)).shots,4,'restart resets shots');
   // Keyboard needs focus and must launch the shot, not merely move the HUD.
   await page.keyboard.press('ArrowLeft');
   await page.keyboard.press('ArrowDown');
   await page.keyboard.press('Space');
   assert.equal((await page.evaluate(()=>window.__slingTest.state)).used,1,'keyboard fired real projectile');
   await page.locator('#pause').click();
   assert.equal(await page.locator('#pause').getAttribute('aria-pressed'),'true','pause works');
   await page.locator('#overlayRetry').click();
   assert.equal((await page.evaluate(()=>window.__slingTest.state)).used,0,'pause can restart level');
   await page.locator('#pause').click();
   await page.locator('#overlayAction').click();
   assert.equal(await page.locator('#pause').getAttribute('aria-pressed'),'false','resume works');
   await page.locator('#settingsButton').click();
   assert.equal(await page.locator('#settingsDialog').evaluate(e=>e.open),true,'settings opens');
   await page.locator('#sound').click();
   assert.equal(await page.locator('#sound').getAttribute('aria-pressed'),'false','sound toggle');
   await page.locator('#settingsClose').click();
   await page.reload();
   assert.equal(await page.locator('#sound').getAttribute('aria-pressed'),'false','sound setting persists');
  }
  assert.deepEqual(errors,[],'no page exceptions at '+width);
  results.push({width,overflow,touchTested:width===320||width===390,errors:errors.length});
  await page.close();
 }
 console.log('Sling Siege browser gate PASS',JSON.stringify(results));
}finally{await browser.close()}
