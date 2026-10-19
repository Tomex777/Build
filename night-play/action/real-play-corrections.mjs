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
