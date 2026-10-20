import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const file=routes[new URL(req.url,'http://localhost').pathname];
 if(!file){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
const url='http://127.0.0.1:'+server.address().port;
const report=[];
async function pageFor(game,random=.5){
 const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 page.on('pageerror',error=>{throw error});
 await page.addInitScript(n=>{Math.random=()=>n},random);
 await page.goto(url+'/action/?game='+game);
 await page.locator('#overlayStart').click();
 return page;
}
const snap=page=>page.evaluate(()=>window.__nightActionTest.snapshot());
async function waitFor(page,condition,timeout=7500){
 await page.waitForFunction(condition,null,{timeout,polling:16});return snap(page);
}
async function shot(page,name){
 const bytes=await page.screenshot({type:'jpeg',quality:66});
 console.log('NIGHT_ACTION_CORRECTED_'+name+'_BASE64='+bytes.toString('base64'));
 await page.screenshot({path:path.join(output,'action-corrected-'+name.toLowerCase()+'.jpg'),type:'jpeg',quality:80});
}
try{
 // 1. A player who does nothing is hit by the FIRST low-flying dinosaur.
 {
  const page=await pageFor('runner',.18);
  const before=await waitFor(page,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='flying'));
  assert.equal(before.obstacles[0].kind,'flying');
  const dead=await waitFor(page,()=>window.__nightActionTest.snapshot().over,6500);
  assert.ok(dead.score>0&&dead.passed===0,'flying enemy catches the idle runner instead of harmlessly passing');
  report.push({game:'runner',case:'idle vs low flyer',score:dead.score,over:dead.over});
  await shot(page,'runner-death');await page.close();
 }
 // 2. Use a real keyboard jump timed by actual incoming obstacle X;
 // it must clear the low flyer, not rely on the collision flag being disabled.
 {
  const page=await pageFor('runner',.18);
  await waitFor(page,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='flying'&&o.x<192));
  assert.equal((await snap(page)).over,false);
  await page.keyboard.press('Space');
  const airborne=await snap(page);assert.ok(airborne.player.vy<0,'real jump input fires');
  const clear=await waitFor(page,()=>window.__nightActionTest.snapshot().over||
   window.__nightActionTest.snapshot().passed>=1,4500);
  assert.equal(clear.over,false,'well-timed jump evades the low flying obstacle');
  assert.ok(clear.passed>=1,'the obstacle visibly exits the running lane');
  report.push({game:'runner',case:'jump over low flyer',passed:clear.passed,over:clear.over});
  await shot(page,'runner-jump');await page.close();
 }
 // 3. An upper glider must catch an upright player but ducking can pass.
 {
  const page=await pageFor('runner',.4);
  await waitFor(page,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='glider'&&o.x<175));
  await page.keyboard.down('ArrowDown');
  assert.equal((await snap(page)).player.duck,true);
  const clear=await waitFor(page,()=>window.__nightActionTest.snapshot().over||
   window.__nightActionTest.snapshot().passed>=1,4200);
  assert.equal(clear.over,false,'ducking protects against upper airborne enemy');
  await page.keyboard.up('ArrowDown');
  report.push({game:'runner',case:'duck beneath high glider',passed:clear.passed,over:clear.over});
  await shot(page,'runner-duck');await page.close();
 }
 // 4. A truly sliced fruit leaves the intact-fruit array and becomes TWO
 // independently moving pieces, with visible juice droplets on the canvas.
 {
  const page=await pageFor('slice',.5);
  await waitFor(page,()=>window.__nightActionTest.snapshot().fruits.some(f=>f.kind!=='bomb'&&Math.abs(f.vy)<140&&f.y>110&&f.y<450));
  const fruit=(await snap(page)).fruits.find(f=>f.kind!=='bomb'&&Math.abs(f.vy)<140&&f.y>110&&f.y<450);
  assert.ok(fruit);
  // A genuine launch must reach the upper half of the board (older arcs peaked below y=340).
  assert.ok(fruit.y<270,'fruit reaches the upper half of the playfield, not just the bottom edge');
  const box=await page.locator('canvas').boundingBox();
  const x=box.x+fruit.x/390*box.width,y=box.y+fruit.y/550*box.height;
  await page.mouse.move(x-42,y);await page.mouse.down();
  await page.mouse.move(x+42,y,{steps:4});await page.mouse.up();
  const cut=await snap(page);
  assert.equal(cut.score,10,'one actual gesture counts one cut');
  assert.equal(cut.halves.length,2,'fruit is physically replaced with left/right pieces');
  assert.ok(cut.splashes.length>=12,'visible juice spray appears');
  assert.ok(cut.fruits.every(f=>f.hit!==true),'intact sliced fruit is removed');
  await page.waitForTimeout(130);
  const later=await snap(page);
  assert.ok(Math.hypot(later.halves[0].x-later.halves[1].x,later.halves[0].y-later.halves[1].y)>12,'pieces fly apart after the swipe');
  report.push({game:'slice',case:'gesture makes two separate pieces',score:later.score,halves:later.halves.length});
  await shot(page,'fruit-halves');await page.close();
 }
 // A real MOBILE touch gesture must cut; tapping or swiping empty space
 // must never award points. The split is preserved in the screenshot.
 {
  const page=await pageFor('slice',.5);
  const canvas=page.locator('canvas'),box=await canvas.boundingBox();
  const coord=(x,y)=>({x:box.x+x/390*box.width,y:box.y+y/550*box.height});
  const client=await page.context().newCDPSession(page);
  const swipe=async(x1,y1,x2,y2)=>{
   await client.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[coord(x1,y1)]});
   for(let i=1;i<=8;i++){
    await client.send('Input.dispatchTouchEvent',{type:'touchMove',
     touchPoints:[coord(x1+(x2-x1)*i/8,y1+(y2-y1)*i/8)]});
    await page.waitForTimeout(12);
   }
   await client.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  };
  await waitFor(page,()=>window.__nightActionTest.snapshot().fruits.some(f=>
   f.kind!=='bomb'&&Math.abs(f.vy)<180&&f.y>115&&f.y<285));
  let fruit=(await snap(page)).fruits.find(f=>f.kind!=='bomb'&&Math.abs(f.vy)<180&&f.y>115&&f.y<285);
  assert.ok(fruit.apex>=137&&fruit.apex<=233,'launch targets reachable upper/middle screen');
  await page.touchscreen.tap(coord(fruit.x,fruit.y).x,coord(fruit.x,fruit.y).y);
  assert.equal((await snap(page)).score,0,'a stationary touch on fruit is NOT a blade cut');
  await swipe(55,45,310,45);
  assert.equal((await snap(page)).score,0,'no points for an empty-air slash');
  // The idle/tap tests take real time. Reacquire a fruit at its apex;
  // aiming from an older frame makes a swipe miss a fast-moving target.
  await waitFor(page,()=>window.__nightActionTest.snapshot().fruits.some(f=>
   f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<65&&f.y>100&&f.y<315),5000);
  fruit=(await snap(page)).fruits.find(f=>
   f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<65&&f.y>100&&f.y<315);
  assert.ok(fruit,'a fresh reachable fruit reaches its apex');
  const target={x:fruit.x,y:fruit.y,vy:fruit.vy};
  await swipe(fruit.x-49,fruit.y,fruit.x+49,fruit.y);
  const result=await snap(page);
  console.log('NIGHT_FRUIT_TOUCH_DEBUG='+JSON.stringify({
   target,score:result.score,trail:result.trail,
   nearby:result.fruits.map(f=>({x:f.x,y:f.y,vy:f.vy,hit:f.hit,kind:f.kind})),
   halves:result.halves.length}));
  assert.ok(result.score>=10,'an actual finger swipe across a fruit earns points');
  assert.ok(result.halves.length>=2,'a finger slice creates independently falling pieces');
  await page.waitForTimeout(70);
  const halves=(await snap(page)).halves;
  assert.ok(Math.hypot(halves[0].x-halves[1].x,halves[0].y-halves[1].y)>18,
   'two halves visibly separate during the follow-through');
  await shot(page,'fruit-real-touch');
  report.push({game:'slice',case:'real touch tap, miss, valid slice',score:result.score,pieces:result.halves.length});
  await page.close();
 }
 // With zero input, fruit falls back off-screen, subtracts lives and ends
 // the round; missed fruit MUST NOT get a free score.
 {
  const page=await pageFor('slice',.5);
  const firstMiss=await waitFor(page,()=>window.__nightActionTest.snapshot().misses>=1,5000);
  assert.equal(firstMiss.score,0,'missing fruit never increases the score');
  assert.equal(firstMiss.misses>=1,true,'untouched fruit costs a life after falling');
  await shot(page,'fruit-miss');
  const endState=await waitFor(page,()=>window.__nightActionTest.snapshot().over,12500);
  assert.equal(endState.misses,5,'five uncaught fruits end the round');
  assert.equal(endState.score,0);
  assert.equal(await page.locator('#overlay').isVisible(),true,'a missed round shows the restart overlay');
  await page.locator('#overlayStart').click();
  assert.equal((await snap(page)).misses,0,'restart restores all chances');
  report.push({game:'slice',case:'natural missed fruit and restart',misses:endState.misses,score:endState.score});
  await page.close();
 }
 // 5. Centre-controlled ball impacts only ONE brick at a time, rebounds in
 // its physical collision axis, and cannot tunnel along a full row.
 {
  const page=await pageFor('bricks',.5);
  assert.equal((await snap(page)).bricks.length,42);
  await page.locator('canvas').click({position:{x:195,y:290}});
  assert.equal((await snap(page)).attached,false,'real tap launches the ball');
  const hit=await waitFor(page,()=>window.__nightActionTest.snapshot().score>0,3500);
  assert.equal(hit.bricks.length,41,'single collision removes a single first-row brick');
  assert.equal(hit.score,15,'one brick is scored, not an entire line');
  assert.ok(hit.ball.vy>0,'first contact turns the ball downward');
  await page.waitForTimeout(1200);
  const next=await snap(page);
  assert.ok(next.bricks.length>=38,'ball cannot sweep an entire row in a single shot');
  assert.ok(next.bricks.length<=41,'ball progresses through real bounces');
  report.push({game:'bricks',case:'one-brick swept impact',bricksLeft:next.bricks.length,score:next.score});
  await shot(page,'brick-impact');await page.close();
 }
 console.log('NIGHT_ACTION_REAL_PLAY_CORRECTIONS='+JSON.stringify(report));
}finally{
 await browser.close();await new Promise(resolve=>server.close(resolve));
}
