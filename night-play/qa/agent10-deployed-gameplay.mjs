/**
 * Agent 10: independent, real-Chromium gameplay gate for a DEPLOYED NIGHT Play
 * website. Run from the repository root after npm install --no-save playwright:
 * NIGHT_PLAY_BASE_URL=https://<READY-deployment> node night-play/qa/agent10-deployed-gameplay.mjs
 *
 * Uses genuine Chrome DevTools touch events rather than calling engine methods.
 * Default is the canonical combined-preview alias so pre-promotion builds FAIL
 * when users still see stale gameplay. Captures JPG evidence, not fake mocks.
 * No multiplayer claim: without authenticated WhatsApp host, a two-device
 * authoritative room test remains blocked and must be performed separately.
 */
import assert from 'node:assert/strict';
import { mkdirSync,writeFileSync } from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const BASE=(process.env.NIGHT_PLAY_BASE_URL||'https://night-play-combined-preview.vercel.app').replace(/\/$/,'');
const ARTIFACT_DIR=path.resolve(process.env.NIGHT_QA_ARTIFACT_DIR||'night-play/visual-output/agent10');
mkdirSync(ARTIFACT_DIR,{recursive:true});
const result={source:BASE,startedAt:new Date().toISOString(),checks:[],failures:[],notes:[]};
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const snap=page=>page.evaluate(()=>window.__nightActionTest.snapshot());
const attempt=async(name,action)=>{
  try {const details=await action();result.checks.push({name,status:'PASS',...details});console.log('PASS '+name+' '+JSON.stringify(details).slice(0,480));}
  catch(error){const failure={name,error:String(error)};result.failures.push(failure);result.checks.push({name,status:'FAIL',error:String(error)});console.error('FAIL '+name+': '+error);}
};
async function open(game,width=390,height=844,random){
 const context=await browser.newContext({viewport:{width,height},hasTouch:width<=440,isMobile:width<=440});
 const page=await context.newPage(),errors=[];
 if(random!==undefined)await page.addInitScript(n=>{Math.random=()=>n;},random);
 page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(BASE+'/action/?game='+game,{waitUntil:'domcontentloaded',timeout:30000});
 await page.locator('canvas').waitFor();
 return {context,page,errors};
}
async function shot(page,name){
 await page.screenshot({path:path.join(ARTIFACT_DIR,'agent10-'+name+'.jpg'),type:'jpeg',quality:75});
}
async function touch(page,points){
 const dev=await page.context().newCDPSession(page);
 try {
  await dev.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:1,x:points[0][0],y:points[0][1]}]});
  for(const [x,y] of points.slice(1)){
   await dev.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{id:1,x,y}]});
   await page.waitForTimeout(18);
  }
  await dev.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 }finally {await dev.detach()}
}
async function until(page,condition,timeout=8000){
 await page.waitForFunction(condition,null,{timeout,polling:25});
 return snap(page);
}

try {
 for(const [width,height] of [[320,700],[360,800],[390,844],[412,915],[1280,800]]){
  await attempt('fruit viewport '+width,async()=>{
   const {context,page,errors}=await open('slice',width,height);
   try {
    const layout=await page.evaluate(()=>({contentWidth:document.documentElement.scrollWidth,innerWidth}));
    const rect=await page.locator('canvas').boundingBox();
    assert.ok(layout.contentWidth<=width+1,'horizontal overflow');
    assert.ok(rect.x>=0&&rect.x+rect.width<=width+1,'canvas clipped');
    assert.deepEqual(errors,[],'JavaScript page exceptions');
    if([320,390,1280].includes(width))await shot(page,'fruit-'+width);
    return {layout,canvasWidth:Math.round(rect.width)};
   }finally{await context.close()}
  });
 }
 await attempt('real empty swipe, high fruit arc and cut/split',async()=>{
  const {context,page,errors}=await open('slice',390,844,.5);
  try{
   await page.locator('#overlayStart').click();
   const rect=await page.locator('canvas').boundingBox();
   const at=(x,y)=>[rect.x+x/390*rect.width,rect.y+y/550*rect.height];
   await touch(page,[at(15,50),at(115,50),at(200,50)]);
   assert.equal((await snap(page)).score,0,'an empty gesture must not award points');
   const observed=[];
   for(let i=0;i<95;i++){
    const s=await snap(page);
    observed.push(...s.fruits.filter(f=>f.kind!=='bomb').map(f=>f.y));
    await page.waitForTimeout(28);
   }
   assert.ok(observed.length>10,'spawned fruit must be observable');
   const apex=Math.min(...observed);
   assert.ok(apex<275,'fruit must reach the upper half, actual minimum Y='+apex.toFixed(1));
   await shot(page,'fruit-arc');
   await until(page,()=>window.__nightActionTest.snapshot().fruits.some(f=>f.kind!=='bomb'&&f.y>130&&f.y<430&&Math.abs(f.vy)<300),7000);
   const fruit=(await snap(page)).fruits.find(f=>f.kind!=='bomb'&&f.y>130&&f.y<430&&Math.abs(f.vy)<300);
   await touch(page,[at(fruit.x-42,fruit.y),at(fruit.x,fruit.y),at(fruit.x+42,fruit.y)]);
   const cut=await snap(page);
   await shot(page,'fruit-just-cut');
   assert.equal(cut.score,10,'one genuine swipe counts one scored cut');
   assert.equal(cut.halves.length,2,'actual fruit removed into two pieces');
   assert.ok(cut.splashes.length>=12,'visible juice particles appear');
   await page.waitForTimeout(165);
   const after=await snap(page);
   const gap=Math.hypot(after.halves[0].x-after.halves[1].x,after.halves[0].y-after.halves[1].y);
   assert.ok(gap>12,'two fruit halves must fly apart after the swipe');
   await shot(page,'fruit-separated');
   assert.deepEqual(errors,[],'no JavaScript exceptions');
   return {apexLogicalY:Math.round(apex),score:cut.score,halves:cut.halves.length,juice:cut.splashes.length,pieceSeparation:Math.round(gap)};
  }finally{await context.close()}
 });
 await attempt('miss five fruit, lose and restart',async()=>{
  const {context,page,errors}=await open('slice',390,844,.75);
  try{
   await page.locator('#overlayStart').click();
   const dead=await until(page,()=>window.__nightActionTest.snapshot().over,20000);
   assert.equal(dead.misses,5,'five real missed fruit terminate the round');
   await shot(page,'fruit-five-misses');
   await page.locator('#overlayStart').click();
   const restart=await snap(page);
   assert.ok(restart.started&&!restart.over&&restart.score===0&&restart.misses===0,'restart restores fresh playable run');
   assert.deepEqual(errors,[]);
   return {misses:dead.misses,restarted:restart.started};
  }finally{await context.close()}
 });
 for(const mode of ['idle','jump']){
  await attempt('runner first low flyer '+mode,async()=>{
   const {context,page,errors}=await open('runner',390,844,.18);
   try{
    await page.locator('#overlayStart').click();
    const seen=await until(page,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='flying'&&o.x<195),8000);
    assert.equal(seen.obstacles[0].kind,'flying');
    if(mode==='jump')await page.locator('[data-control=jump]').tap();
    const final=await until(page,()=>{const s=window.__nightActionTest.snapshot();return s.over||s.passed>=1},7500);
    assert.equal(final.over,mode==='idle','low flyer has genuine collision consequences');
    if(mode==='jump')assert.ok(final.passed>=1,'timed touchscreen jump genuinely passes obstacle');
    else{
     await page.locator('#overlayStart').click();
     const reset=await snap(page);
     assert.ok(reset.started&&!reset.over&&reset.score===0,'can restart after collision');
    }
    await shot(page,'runner-'+mode);
    assert.deepEqual(errors,[]);
    return {over:final.over,passed:final.passed,score:final.score};
   }finally{await context.close()}
  });
 }
 await attempt('bricks real finger movement, launch and single collision',async()=>{
  const {context,page,errors}=await open('bricks',390,844,.5);
  try{
   await page.locator('#overlayStart').click();
   const rect=await page.locator('canvas').boundingBox();
   await touch(page,[[rect.x+rect.width*.60,rect.y+rect.height*.85],[rect.x+rect.width*.78,rect.y+rect.height*.85]]);
   const moved=await snap(page);
   assert.ok(moved.paddle>250,'finger controls paddle');
   await page.locator('canvas').tap({position:{x:rect.width/2,y:rect.height*.6}});
   const hit=await until(page,()=>window.__nightActionTest.snapshot().score>0,5500);
   assert.equal(hit.score,15,'one brick contact earns one first score');
   assert.equal(hit.bricks.length,41,'one collision cannot clear a whole row');
   await shot(page,'bricks-hit');
   assert.deepEqual(errors,[]);
   return {paddle:moved.paddle,score:hit.score,bricksLeft:hit.bricks.length};
  }finally{await context.close()}
 });
 await attempt('jumper real held finger and auto-bounce',async()=>{
  const {context,page,errors}=await open('jump',390,844,.5);
  const dev=await page.context().newCDPSession(page);
  try{
   await page.locator('#overlayStart').click();
   const rect=await page.locator('canvas').boundingBox(),at=x=>[rect.x+x/390*rect.width,rect.y+rect.height*.65];
   const initial=await snap(page);
   await dev.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:1,x:at(170)[0],y:at(170)[1]}]});
   let max=0,bounces=0,lastVy=initial.player.vy;
   for(let i=0;i<90;i++){
    const s=await snap(page);max=Math.max(max,s.score);
    if(lastVy>0&&s.player.vy<0)bounces++;
    lastVy=s.player.vy;if(s.over)break;
    const target=s.platforms.find(q=>q.y<s.player.y+10&&q.y>s.player.y-95)||s.platforms[0];
    const xy=at(target?target.x+target.w/2:195);
    await dev.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{id:1,x:xy[0],y:xy[1]}]});
    await page.waitForTimeout(65);
   }
   await dev.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   assert.ok(initial.platforms.length>=8,'proper platform board');
   assert.ok(max>=80&&bounces>=1,'must successfully land/jump, not just show a screenshot');
   await shot(page,'jumper-steered');
   assert.deepEqual(errors,[]);
   return {maxHeight:max,bounces};
  }finally{await dev.detach();await context.close()}
 });
 await attempt('shooter touch movement, enemy, projectile and score',async()=>{
  const {context,page,errors}=await open('shooter',390,844,.5);
  try{
   await page.locator('#overlayStart').click();
   const rect=await page.locator('canvas').boundingBox();
   const at=x=>[rect.x+x/390*rect.width,rect.y+488/550*rect.height];
   const start=await snap(page);
   await touch(page,[at(195),at(260),at(330)]);
   const moved=await snap(page);
   await page.waitForTimeout(2400);
   const after=await snap(page);
   assert.ok(moved.ship.x>start.ship.x+40,'ship follows real drag');
   assert.ok(after.clock>2&&after.shots.length>0&&after.enemies.length>0,'engine advances shots and enemies');
   await shot(page,'shooter-live');
   assert.deepEqual(errors,[]);
   return {before:start.ship.x,after:moved.ship.x,shots:after.shots.length,score:after.score};
  }finally{await context.close()}
 });
}finally{
 result.finishedAt=new Date().toISOString();
 result.notes.push('Screenshots require human review; test pass does not certify artwork quality or physical device latency.');
 result.notes.push('Two authoritative live NOMI/party browsers require real host auth and server-connected invite, not a mocked HTTP route.');
 writeFileSync(path.join(ARTIFACT_DIR,'agent10-result.json'),JSON.stringify(result,null,2));
 await browser.close();
}
console.log('AGENT10_QA_SUMMARY '+JSON.stringify({base:BASE,passed:result.checks.length-result.failures.length,total:result.checks.length,failures:result.failures}).slice(0,7000));
if(result.failures.length)process.exitCode=1;
