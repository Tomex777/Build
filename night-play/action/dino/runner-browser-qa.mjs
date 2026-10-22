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
 'action/dino/index.html','action/dino/style.css','action/dino/bridge.js','action/dino/night-skin.js',
 'action/dino/skyline.svg',
 'action/dino/chromium-runner.js','game-shell.css'
]);
const server=createServer((req,res)=>{
 const path=new URL(req.url,'http://localhost').pathname;
 let file=path.startsWith('/action/dino/')?path==='/action/dino/'?'action/dino/index.html':path.slice(1):
  path==='/action/'?'action/index.html':path.slice(1);
 if(!allowed.has(file)){res.writeHead(404);res.end();return}
 res.writeHead(200,{'content-type':file.endsWith('.svg')?'image/svg+xml':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
 res.end(readFileSync(join(root,file)));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const origin='http://127.0.0.1:'+server.address().port;
const snap=page=>page.evaluate(()=>window.__nightDinoTest.snapshot());
let browser;
try{
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[1280,800]]){
  const page=await browser.newPage({viewport:{width,height},hasTouch:true,isMobile:width<700});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/action/?game=runner',{waitUntil:'networkidle'});
  await page.waitForURL('**/action/dino/');
  await page.waitForFunction(()=>window.__nightDinoTest?.snapshot().ready);
  // The skyline is part of the original runner's identity, not a 404'd decoration.
  assert.equal((await page.request.get(origin+'/action/dino/skyline.svg')).status(),200,
    'original-inspired local skyline asset is served');
  assert.equal(await page.locator('.dino-skyline').count(),1,'old runner skyline present');
  const initial=await snap(page);
  assert.equal(initial.gravity,.6,'original Chromium gravity');
  assert.equal(await page.evaluate(()=>typeof window.NightDinoSkin?.install),'function',
    'classic human artwork installer present');
  assert.ok(await page.evaluate(()=>{
   const img=document.querySelector('#offline-resources-2x');
   const art=document.createElement('canvas');art.width=img.naturalWidth;art.height=img.naturalHeight;
   const ctx=art.getContext('2d');ctx.drawImage(img,0,0);const rgba=ctx.getImageData(1678+2*24,2+2*20,1,1).data;
   return rgba[1]>rgba[0]*.8 || rgba[0]>10;
  }),'custom runner skin atlas successfully installed');
  assert.equal(initial.jumpVelocity,-10,'original Chromium T-Rex jump velocity');
  assert.equal(initial.groundY,93,'original Chromium ground geometry');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  assert.ok(overflow<=1,'no horizontal overflow at '+width);
  const arena=await page.locator('#stage').boundingBox();
  const rect=await page.locator('.runner-canvas').boundingBox();
  // Runner remains as wide as the other games but its height is deliberately
  // reduced: no enormous empty sky. Surrounding whitespace is tappable.
  const other=await browser.newPage({viewport:{width,height},hasTouch:true,isMobile:width<700});
  await other.goto(origin+'/action/?game=slice',{waitUntil:'domcontentloaded'});
  const reference=await other.locator('#screen').boundingBox();
  assert.ok(Math.abs(arena.width-reference.width)<=4,'Dino and Fruit Slice widths align at '+width);
  const heightRatio=arena.height/reference.height;
  assert.ok(heightRatio>=.76&&heightRatio<=.92,'compact stage must be noticeably shorter than Fruit Slice at '+width+
    ': Dino='+arena.height+', Slice='+reference.height);
  await other.close();
  assert.ok(Math.abs(rect.height-arena.height)<=3,
    'the full Chromium canvas, not a short 150px strip, must fill the game height at '+width);
  assert.ok(initial.zoom>=1.3,'uniform camera zoom keeps sprites legible in tall arena');
  assert.ok(rect.width>290&&rect.width<=600,'Chromium native game width');
  assert.equal(initial.groundY,93,'physics coordinates remain original 150px world despite tall rendering');
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
   // Real tap on title OUTSIDE the game also jumps, Chrome-offline style.
   const intro=await page.locator('.night-game-intro h1').boundingBox();
   const outerX=intro.x+intro.width/2,outerY=intro.y+intro.height/2;
   await input.send('Input.dispatchTouchEvent',{type:'touchStart',
     touchPoints:[{id:3,x:outerX,y:outerY}]});
   await page.waitForTimeout(135);
   const outside=await snap(page);
   assert.ok(outside.jumping&&outside.dinosaurY<outside.groundY-12,
     'tapping the page outside the arena jumps immediately');
   await input.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   await page.waitForTimeout(520);
   assert.equal((await snap(page)).jumping,false,'external jump lands correctly');
   assert.equal(await page.locator('#duckButton').count(),0,'no visible duck dock/control');
   // Recognizable single-surface swipe down triggers original Chromium duck.
   const dx=x,dy=y-55;
   await input.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:2,x:dx,y:dy}]});
   await input.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{id:2,x:dx,y:dy+36}]});
   await page.waitForTimeout(85);
   assert.equal((await snap(page)).ducking,true,'swipe down crouches without dock button');
   await input.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   assert.equal((await snap(page)).ducking,false,'swipe release stands');
   await page.locator('#pauseButton').click();
   const paused=await snap(page);
   assert.equal(paused.jumping,false,'pause button must not cause a global jump');
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
   // Tapping an empty area OUTSIDE the playfield also restarts a dead run,
   // just as tapping Chrome's offline page does.
   await input.send('Input.dispatchTouchEvent',{type:'touchStart',
     touchPoints:[{id:4,x:outerX,y:outerY}]});
   await page.waitForTimeout(110);
   assert.equal((await snap(page)).dead,false,'outer-page tap restarts after collision');
   await input.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   await page.waitForTimeout(100);
   assert.ok((await snap(page)).distanceRan<200,'new round resets distance');
  }
  assert.deepEqual(errors,[],'no browser exceptions at '+width);
  console.log('PASS',width,{overflow,arenaHeight:arena.height,canvasHeight:rect.height,zoom:initial.zoom,referenceHeight:reference.height,heightRatio,originalJumpVelocity:initial.jumpVelocity,canvasWidth:rect.width,skin:'NIGHT'});
  await page.close();
 }
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
