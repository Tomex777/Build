import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action':'action/index.html','/action/style.css':'action/style.css','/action/games.js':'action/games.js','/game-shell.css':'game-shell.css','/games':'index.html'};
const server=createServer((req,res)=>{
 const file=routes[new URL(req.url,'http://localhost').pathname];
 if(!file){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
try{
 const url='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 page.on('pageerror',error=>{throw new Error('Page exception: '+String(error))});
 const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
 const snap=()=>page.evaluate(()=>window.__nightActionTest.snapshot());
 for(const [game,title] of [['slice','Fruit Slice'],['jump','Sky Jumper'],['runner','Endless Runner'],['shooter','Space Shooter'],['bricks','Brick Breaker']]){
  await page.goto(url+'/action/?game='+game);
  assert.equal(await page.locator('#title').innerText(),title,'correct game loads');
  assert.equal(await page.locator('.action-overlay').isVisible(),true,'rules and Start shown before gameplay');
  assert.equal(await page.locator('.night-brand-mark use').getAttribute('href'),'#night-mark','approved NIGHT brand');
  const metrics=await page.locator('canvas').evaluate(el=>{
   const r=el.getBoundingClientRect();return {w:r.width,h:r.height,left:r.left,right:r.right,scrollWidth:document.documentElement.scrollWidth,windowWidth:innerWidth};
  });
  assert.ok(metrics.w>=370&&metrics.right<=390.5&&metrics.scrollWidth<=392,'portrait board fills phone without overflow: '+game+' '+JSON.stringify(metrics));
  const first=await snap();assert.equal(first.game,game);assert.equal(first.started,false);
  await page.locator('#overlayStart').click();assert.equal((await snap()).started,true,'Start begins '+game);
  await page.waitForTimeout(game==='slice'?1650:1250);
  const running=await snap();
  console.log('NIGHT_ACTION_STATE_'+game.toUpperCase()+'='+JSON.stringify({frame:running.frame,over:running.over,score:running.score,started:running.started}));
  assert.ok(running.frame>4,'animation frame runs for '+game);
  if(game==='slice')assert.ok(running.fruits.length>0,'Fruit Slice launches actual fruit or bombs');
  if(game==='jump'){
   assert.ok(running.platforms.length>=8,'Sky Jumper has a scrolling platform set');
   assert.equal(await page.locator('#stat').isVisible(),false,'height is not duplicated as score');
   assert.equal(await page.locator('#score').isVisible(),true,'one authoritative score remains');
   const mover=running.platforms.find(p=>p.moving);
   assert.ok(mover,'moving platforms appear at the very start of the game');
   assert.ok(running.platforms.every(p=>p.fragile===undefined&&p.ttl===undefined),'platforms cannot crack, time out or disappear on landing');
   assert.equal(running.hazardY,undefined,'no rising hazard forces a timed death');
   assert.ok(running.platforms.every(p=>p.w>=55),'landing platforms remain wide enough to aim for');
   // Sample several real gameplay frames, not just two instants. A sine
   // platform can return to nearly the same X near its turning point even
   // though its collision body has moved during the interval.
   const positions=[mover.x];
   for(const delay of [170,170,200]){
    await page.waitForTimeout(delay);
    const updated=await snap(),nextMover=updated.platforms.find(p=>p.baseX===mover.baseX&&p.phase===mover.phase);
    assert.ok(nextMover,'the first moving platform remains present while on screen');
    positions.push(nextMover.x);
   }
   assert.ok(Math.max(...positions)-Math.min(...positions)>0.35,
    'the moving platform visibly changes its true physical collision position');
 }
  if(game==='runner')assert.ok(running.score>0&&running.speed>220,'Runner progresses and accelerates');
  if(game==='shooter')assert.ok(running.shots.length>0&&running.enemies.length>0,'Shooter generates shots and actual enemies');
  if(game==='bricks')assert.equal(running.bricks.length,42,'Brick Breaker creates the full first board');
  await page.screenshot({path:path.join(output,'action-'+game+'-390x844.jpg'),type:'jpeg',quality:73});
  console.log('NIGHT_ACTION_VISUAL_'+game.toUpperCase()+'_BASE64='+(await page.screenshot({type:'jpeg',quality:54})).toString('base64'));
  if(game==='slice'){
   // Never chase a 600px/s fruit using a screenshot-era coordinate:
   // wait for an actual reachable apex, then move the mouse across it.
   await page.waitForFunction(()=>window.__nightActionTest.snapshot().fruits.some(f=>
    f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<85&&f.y>80&&f.y<335),
    null,{timeout:5500,polling:16});
   const picked=(await snap()).fruits.find(f=>
    f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<85&&f.y>80&&f.y<335);
   assert.ok(picked,'a fruit actually reaches the upper slicing lane');
   const box=await page.locator('canvas').boundingBox();
   const x=box.x+picked.x/390*box.width,y=box.y+picked.y/550*box.height;
   const before=(await snap()).score;
   await page.mouse.move(x-42,y);await page.mouse.down();
   await page.mouse.move(x+42,y,{steps:4});await page.mouse.up();
   await page.waitForTimeout(60);
   assert.ok((await snap()).score>=before+10,'a timed real swipe can slice fruit');
  }
  if(game==='runner'){
   await page.keyboard.press('Space');await page.waitForTimeout(70);
   assert.ok((await snap()).player.y<442,'keyboard jump gives upward movement');
   assert.ok(await page.locator('[data-control="jump"]').isVisible());
   await page.locator('[data-control="slide"]').dispatchEvent('pointerdown',{pointerType:'touch',pointerId:17,bubbles:true});
   assert.equal((await snap()).player.duck,true);
   await page.locator('[data-control="slide"]').dispatchEvent('pointerup',{pointerType:'touch',pointerId:17,bubbles:true});
   assert.equal((await snap()).player.duck,false);
  }
  if(game==='bricks'){
   assert.equal((await snap()).attached,true);
   await page.locator('canvas').click({position:{x:160,y:260}});
   await page.waitForTimeout(120);
   assert.equal((await snap()).attached,false,'tapping launches ball');
   const old=(await snap()).paddle;
   const box=await page.locator('canvas').boundingBox();
   await page.mouse.move(box.x+box.width*.3,box.y+box.height*.85);
   await page.mouse.down();await page.mouse.move(box.x+box.width*.8,box.y+box.height*.85,{steps:5});await page.mouse.up();
   assert.ok((await snap()).paddle>old+35,'paddle tracks gesture');
  }
  if(game==='shooter'){
   const before=(await snap()).ship.x;
   const box=await page.locator('canvas').boundingBox();
   await page.mouse.move(box.x+box.width*.5,box.y+box.height*.8);await page.mouse.down();
   await page.mouse.move(box.x+box.width*.86,box.y+box.height*.8,{steps:5});
   await page.waitForTimeout(120);await page.mouse.up();
   assert.ok((await snap()).ship.x>before+25,'ship follows finger');
  }
  if(game==='jump'){
   assert.equal(await page.locator('#touchControls button').count(),2);
  }
  const saved=await snap();
  for(const [w,h] of [[320,568],[360,740],[390,844],[412,915],[768,1024],[900,450]]){
   await page.setViewportSize({width:w,height:h});
   await page.waitForTimeout(60);
   const r=await page.locator('canvas').evaluate(el=>{
    const rc=el.getBoundingClientRect();return {left:rc.left,right:rc.right,w:rc.width,h:rc.height,scroll:document.documentElement.scrollWidth,viewport:innerWidth};
   });
   assert.ok(r.left>=0&&r.right<=w+1&&r.scroll<=w+2,'no sideways overflow '+game+' '+w+'×'+h+': '+JSON.stringify(r));
   assert.ok(r.w>=100&&r.h>=150,'board keeps playable size '+game+' '+w+'×'+h);
  }
  assert.equal((await snap()).game,saved.game,'resizing cannot change the game');
  await page.setViewportSize({width:390,height:844});
  // A real action round may legitimately end while we exercise multiple
  // slow CI viewports. Begin a fresh round before testing Pause/Resume.
  if((await snap()).over)await page.locator('#overlayStart').click();
  assert.equal((await snap()).started,true,'game can start a fresh playable round');
  await page.locator('#pauseButton').click();
  assert.equal((await snap()).paused,true,'Pause interrupts '+game);
  const pausedFrame=(await snap()).frame;await page.waitForTimeout(100);
  assert.equal((await snap()).frame,pausedFrame,'paused game never advances');
  await page.locator('#overlayStart').click();assert.equal((await snap()).paused,false,'Resume continues');
  await page.locator('#restart').click();assert.equal((await snap()).score,0,'Restart resets score');
  await page.locator('#settingsButton').click();assert.ok(await page.locator('#settingsDialog').isVisible());
  assert.equal(await page.locator('#gamePicks button').count(),5);
  await page.locator('#settingsClose').click();
 }
 console.log('PASS NIGHT ACTION ARCADE: 5 playable modes, pointer/keyboard, motion, overlays, restart, pause and six viewport sizes.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
