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
 // The legacy runner cases now live in the authentic Chromium Dino QA.
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
   // A short, deliberate real fingertip flick: do not let browser scheduler
   // latency move an apex target a full fruit diameter before the cut ends.
   for(let i=1;i<=4;i++){
    await client.send('Input.dispatchTouchEvent',{type:'touchMove',
     touchPoints:[coord(x1+(x2-x1)*i/4,y1+(y2-y1)*i/4)]});
    await page.waitForTimeout(4);
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
 // A blade hitting a bomb terminates the round without fake fruit points.
 {
  const page=await pageFor('slice',.01);
  await waitFor(page,()=>window.__nightActionTest.snapshot().fruits.some(f=>
   f.kind==='bomb'&&!f.hit&&Math.abs(f.vy)<110&&f.y>70&&f.y<390),4500);
  const bomb=(await snap(page)).fruits.find(f=>
   f.kind==='bomb'&&!f.hit&&Math.abs(f.vy)<110&&f.y>70&&f.y<390);
  assert.ok(bomb,'visible bomb becomes reachable in the slicing lane');
  const box=await page.locator('canvas').boundingBox();
  const px=x=>box.x+x/390*box.width,py=y=>box.y+y/550*box.height;
  await page.mouse.move(px(bomb.x-41),py(bomb.y));await page.mouse.down();
  await page.mouse.move(px(bomb.x+41),py(bomb.y),{steps:4});await page.mouse.up();
  const hit=await snap(page);
  assert.equal(hit.over,true,'an actual cut through a bomb ends the round');
  assert.equal(hit.score,0,'cutting a bomb must not award points');
  await shot(page,'fruit-bomb');await page.close();
  report.push({game:'slice',case:'real bomb collision ends round without points',over:hit.over});
 }
 // A high-frequency mobile swipe through a double volley is one physical
 // action that slices BOTH bodies and gives increasing, earned combo points.
 // Seeded RNG controls spawn arrangement; touch moves still go via Chromium CDP.
 {
  const page=await pageFor('slice',.1);
  // Choose the two bodies genuinely passing through their shared apex.
  // Other volleys can coexist farther down the screen, so do not require
  // ALL live fruit to equal two; do require exactly two apex targets.
  const atApex=fruits=>fruits.filter(f=>f.kind!=='bomb'&&!f.hit&&
   Math.abs(f.vy)<75&&f.y>110&&f.y<300);
  // Playwright serializes this function into the browser realm: it cannot
  // capture the Node-side atApex helper from above.
  await waitFor(page,()=>window.__nightActionTest.snapshot().fruits.filter(f=>
   f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<75&&f.y>110&&f.y<300).length===2,5000);
  const targets=atApex((await snap(page)).fruits);
  assert.equal(targets.length,2,'two distinct fruit are at the apex of their real volley');
  assert.ok(Math.abs(targets[0].x-targets[1].x)>80,'volley fruits remain visually distinguishable');
  const targetY=targets[0].y,left=Math.max(6,Math.min(...targets.map(f=>f.x))-44),
   right=Math.min(384,Math.max(...targets.map(f=>f.x))+44);
  const box=await page.locator('canvas').boundingBox();
  const coord=(x,y)=>({x:box.x+x/390*box.width,y:box.y+y/550*box.height});
  const cdp=await page.context().newCDPSession(page);
  const id=204;
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{...coord(left,targetY),id}]});
  for(let i=1;i<=4;i++){
   await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{
    ...coord(left+(right-left)*i/4,targetY),id}]});
   await page.waitForTimeout(4);
  }
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  const hit=await snap(page);
  assert.equal(hit.score,25,'a two-fruit real touchscreen combo scores 10 then 15 earned points');
  assert.equal(hit.halves.length,4,'both genuinely cut fruit split into two visible pieces each');
  assert.ok(hit.popups.some(p=>p.label==='2× COMBO'),'one visible combo indicator rewards the real simultaneous cut');
  await shot(page,'fruit-double-combo');
  report.push({game:'slice',case:'real touch double-fruit combo',score:hit.score,halves:hit.halves.length});
  await page.close();
 }
 // A bomb and fruit intersected by the SAME continuous blade segment
 // cannot grant fruit points just because fruit was inserted first in state.
 // Here the two distinct launch lanes are visible and the low-level CDP
 // swipe intentionally spans BOTH in one native touchMove.
 {
  const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
  page.on('pageerror',error=>{throw error});
  await page.addInitScript(()=>{
   const values=[.1,.5,.5,.5,.5,.5,.5,.5,.5,.5,.01];
   let index=0;Math.random=()=>values[index++]??.5;
  });
  await page.goto(url+'/action/?game=slice');
  await page.locator('#overlayStart').click();
  await waitFor(page,()=>{
   const s=window.__nightActionTest.snapshot();
   return s.fruits.length===2&&s.fruits.some(f=>f.kind==='bomb')&&
    s.fruits.some(f=>f.kind!=='bomb')&&s.fruits.every(f=>Math.abs(f.vy)<70&&f.y>110&&f.y<300);
  },5000);
  const targets=(await snap(page)).fruits,box=await page.locator('canvas').boundingBox();
  assert.equal(targets[0].kind!=='bomb',true,'nonbomb fruit is first in processing order');
  assert.equal(targets[1].kind,'bomb','lethal target is last in processing order');
  const coord=(x,y)=>({x:box.x+x/390*box.width,y:box.y+y/550*box.height,id:415});
  const y=targets[0].y,left=Math.max(4,Math.min(...targets.map(f=>f.x))-45),
   right=Math.min(386,Math.max(...targets.map(f=>f.x))+45);
  const cdp=await page.context().newCDPSession(page);
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[coord(left,y)]});
  await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[coord(right,y)]});
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  const result=await snap(page);
  assert.equal(result.over,true,'the actual cross-board slash hits the bomb');
  assert.equal(result.score,0,'lethal same-segment bomb contact awards no earlier fruit points');
  report.push({game:'slice',case:'one real touch segment intersects bomb and fruit; bomb wins',score:result.score});
  await shot(page,'fruit-bomb-priority');await page.close();
 }
 // Some browsers deliver the last gesture sample as pointerup; previously
 // that final motion was thrown away, causing apparently ignored swipes.
 // This supplemental event-sequencing check uses exact pointerup geometry;
 // the separate tests above exercise full native Chromium CDP touch streams.
 {
  const page=await pageFor('slice',.5);
  await waitFor(page,()=>window.__nightActionTest.snapshot().fruits.some(f=>
   f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<70&&f.y>110&&f.y<300),5000);
  const fruit=(await snap(page)).fruits.find(f=>
   f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<70&&f.y>110&&f.y<300);
  const bounds=await page.locator('canvas').boundingBox(),canvas=page.locator('canvas');
  const coord=(x,y)=>({clientX:bounds.x+x/390*bounds.width,clientY:bounds.y+y/550*bounds.height});
  await canvas.dispatchEvent('pointerdown',{bubbles:true,pointerType:'touch',pointerId:901,
   button:0,...coord(fruit.x-41,fruit.y)});
  await canvas.dispatchEvent('pointerup',{bubbles:true,pointerType:'touch',pointerId:901,
   button:0,...coord(fruit.x+41,fruit.y)});
  const after=await snap(page);
  assert.equal(after.score,10,'final pointerup segment slices without requiring a second gesture');
  assert.equal(after.halves.length,2,'finger lift must produce two visible cut halves');
  report.push({game:'slice',case:'last pointerup segment is not dropped',score:after.score});
  await shot(page,'fruit-finger-lift');
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
