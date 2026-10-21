// NIGHT Play / Chromium Dino. Browser regression using ORIGINAL Chromium engine.
// npm install --no-save playwright@1.56.1; node night-play/action/dino/runner-browser-qa.mjs
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const output=join(root,'visual-output','chromium-dino');
mkdirSync(output,{recursive:true});
const allowed=new Set([
 'action/index.html','action/style.css','action/games.js',
 'action/dino/index.html','action/dino/style.css','action/dino/bridge.js',
 'action/dino/chromium-runner.js','game-shell.css'
]);
const server=createServer((req,res)=>{
 const path=new URL(req.url,'http://localhost').pathname;
 let file=path.startsWith('/action/dino/')?path==='/action/dino/'?'action/dino/index.html':path.slice(1):
  path==='/action/'?'action/index.html':path.slice(1);
 if(!allowed.has(file)){res.writeHead(404);res.end();return}
 res.writeHead(200,{'content-type':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
 res.end(readFileSync(join(root,file)));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const origin='http://127.0.0.1:'+server.address().port;
const snap=page=>page.evaluate(()=>window.__nightDinoTest.snapshot());
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[1280,800]]){
  const page=await browser.newPage({viewport:{width,height},hasTouch:true,isMobile:width<700});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/action/?game=runner',{waitUntil:'networkidle'});
  await page.waitForURL('**/action/dino/');
  await page.waitForFunction(()=>window.__nightDinoTest?.snapshot().ready);
  const initial=await snap(page);
  assert.equal(initial.gravity,.6,'original Chromium gravity');
  assert.equal(initial.jumpVelocity,-10,'original Chromium T-Rex jump velocity');
  assert.equal(initial.groundY,93,'original Chromium ground geometry');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  assert.ok(overflow<=1,'no horizontal overflow at '+width);
  const arena=await page.locator('#stage').boundingBox();
  const rect=await page.locator('.runner-canvas').boundingBox();
  const expectedHeight=height>=800?(width<700?550:500):width<700?340:220;
  assert.ok(arena.height>=expectedHeight,'game arena must stay tall on '+width+'x'+height+': '+arena.height);
  assert.ok(arena.height>=rect.height+150,'more sky/visible space, not a stretched dinosaur');
  assert.equal(rect.height,150,'original pixel-space height, not stretched');
  assert.ok(rect.width>290&&rect.width<=600,'Chromium native game width');
  await page.locator('#overlayAction').click();
  await page.waitForTimeout(390);
  const playing=await snap(page);assert.equal(playing.playing,true,'engine plays');
  assert.ok(playing.distanceRan>0,'original distance movement');
  if(width===390){
   await page.locator('#touchPad').screenshot({path:join(output,'night-dino-active-'+width+'.png')});
   const box=await page.locator('#touchPad').boundingBox();
   const input=await page.context().newCDPSession(page);
   const x=box.x+box.width*.45,y=box.y+box.height*.5;
   await input.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:1,x,y}]});
   await page.waitForTimeout(160);
   const rising=await snap(page);
   assert.ok(rising.dinosaurY<rising.groundY-25,'real finger causes original T-Rex jump');
   await input.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   await page.waitForTimeout(570);
   assert.equal((await snap(page)).jumping,false,'original jump lands');
   const duck=await page.locator('#duckButton').boundingBox();
   const dx=duck.x+duck.width*.5,dy=duck.y+duck.height*.5;
   await input.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:2,x:dx,y:dy}]});
   await page.waitForTimeout(90);
   assert.equal((await snap(page)).ducking,true,'physical DUCK button crouches');
   await input.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   assert.equal((await snap(page)).ducking,false,'releasing DUCK stands');
   await page.locator('#pauseButton').click();
   const paused=await snap(page);
   assert.equal(paused.paused,true,'pause toggled');
   await page.waitForTimeout(100);
   assert.equal((await snap(page)).distanceRan,paused.distanceRan,'pause freezes original engine');
   await page.locator('#overlayAction').click();
   assert.equal((await snap(page)).playing,true,'resume original engine');
   await page.locator('#settingsButton').click();
   assert.equal(await page.locator('#settingsDialog').evaluate(el=>el.open),true,'proper settings');
   await page.locator('#soundToggle').click();
   assert.equal(await page.locator('#soundToggle').getAttribute('aria-pressed'),'false','audio toggle');
   await page.locator('#settingsClose').click();
   await page.locator('#overlayAction').click();
   // Natural spawning and collision, without any control input.
   await page.waitForFunction(()=>window.__nightDinoTest.snapshot().dead,null,{timeout:18000});
   assert.ok((await snap(page)).score>0,'earned score from real game');
   await page.locator('#overlayAction').click();
   assert.equal((await snap(page)).dead,false,'restarts after collision');
   await page.waitForTimeout(100);
   assert.ok((await snap(page)).distanceRan<200,'new round resets distance');
  }
  assert.deepEqual(errors,[],'no browser exceptions at '+width);
  console.log('PASS',width,{overflow,arenaHeight:arena.height,originalJumpVelocity:initial.jumpVelocity,canvasWidth:rect.width,canvasHeight:rect.height});
  await page.close();
 }
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
