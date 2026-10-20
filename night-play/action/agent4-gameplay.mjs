import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action/style.css':'action/style.css','/action/games.js':'action/games.js','/arcade/':'arcade/index.html','/arcade/style.css':'arcade/style.css','/arcade/games.js':'arcade/games.js','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{const file=routes[new URL(req.url,'http://localhost').pathname];if(!file){res.writeHead(404);res.end('404');return;}res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});res.end(readFileSync(path.join(root,file)));});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const origin='http://127.0.0.1:'+server.address().port,output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
let browser;const errors=[];
async function shot(page,name){const bytes=await page.screenshot({path:path.join(output,'agent4-'+name+'.jpg'),type:'jpeg',quality:56});console.log('AGENT4_FRAME_'+name.toUpperCase()+'_BASE64='+bytes.toString('base64'));}
async function sizes(page){for(const [w,h] of [[320,568],[360,740],[390,844],[412,915],[900,750]]){await page.setViewportSize({width:w,height:h});await page.waitForTimeout(60);const b=await page.locator('canvas').evaluate(c=>{const r=c.getBoundingClientRect();return {left:r.left,right:r.right,w:r.width,h:r.height,scroll:document.documentElement.scrollWidth};});assert.ok(b.left>=0&&b.right<=w+1&&b.scroll<=w+2&&b.w>180&&b.h>200,JSON.stringify({w,h,b}));}await page.setViewportSize({width:390,height:844});}
const snap=p=>p.evaluate(()=>window.__nightActionTest.snapshot());
async function actionPage(game){
 const p=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 p.on('pageerror',e=>errors.push(e.message));await p.addInitScript(()=>{Math.random=()=>.5});
 await p.goto(origin+'/action/?game='+game);await p.locator('#overlayStart').click();return p;
}
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 {
  const p=await actionPage('bricks');await sizes(p);
  const initial=await snap(p);assert.equal(initial.bricks.length,42);assert.equal(initial.score,0);
  await shot(p,'bricks-start');
  await p.locator('canvas').click({position:{x:180,y:280}});
  await p.waitForFunction(()=>window.__nightActionTest.snapshot().score>0,null,{timeout:6500,polling:40});
  const hit=await snap(p);assert.equal(hit.bricks.length,41,'one actual brick hit');assert.equal(hit.score,15);assert.ok(Math.hypot(hit.ball.vx,hit.ball.vy)>390,'ball accelerates');
  await shot(p,'bricks-hit');
  const box=await p.locator('canvas').boundingBox();
  for(let i=0;i<280;i++){
   const s=await snap(p);if(s.over)break;
   if(s.attached)await p.locator('canvas').click({position:{x:180,y:270}});
   else if(s.ball.vy>0&&s.ball.y>330){const x=s.ball.x<195?345:35;await p.mouse.move(box.x+box.width*x/390,box.y+box.height*.85);await p.mouse.down();await p.waitForTimeout(45);await p.mouse.up();}
   await p.waitForTimeout(85);
  }
  const lost=await snap(p);
  assert.equal(lost.over,true,'losing requires actual misses');assert.equal(lost.lives,0);
  await shot(p,'bricks-loss');
  await p.locator('#overlayStart').click();assert.equal((await snap(p)).score,0);assert.equal((await snap(p)).level,1);
  console.log('AGENT4_BRICKS='+JSON.stringify({hit:hit.score,lost:lost.lives,restarted:true}));await p.close();
 }
 {
  const p=await actionPage('shooter'),box=await p.locator('canvas').boundingBox();
  await p.mouse.move(box.x+box.width*.09,box.y+box.height*.85);await p.mouse.down();await p.waitForTimeout(190);await p.mouse.up();
  await p.waitForFunction(()=>window.__nightActionTest.snapshot().enemyShots.some(b=>b.y>350),null,{timeout:10000,polling:40});
  const live=await snap(p);assert.ok(live.enemies.length&&live.shots.length);
  await shot(p,'shooter-action');
  await p.mouse.move(box.x+box.width*.5,box.y+box.height*.84);await p.mouse.down();await p.waitForTimeout(400);
  await p.waitForFunction(()=>window.__nightActionTest.snapshot().lives<3,null,{timeout:5700,polling:40});
  await p.mouse.up();const hurt=await snap(p);assert.ok(hurt.lives<3);
  await shot(p,'shooter-damage');await sizes(p);
  await p.locator('#restart').click();assert.equal((await snap(p)).lives,3);
  console.log('AGENT4_SHOOTER='+JSON.stringify({lives:hurt.lives,enemyShots:live.enemyShots.length}));await p.close();
 }
 {
  const p=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
  p.on('pageerror',e=>errors.push(e.message));await p.goto(origin+'/arcade/?game=bird');await sizes(p);
  assert.equal(await p.locator('[data-control="flap"]').count(),0);await shot(p,'flappy-start');
  await p.locator('canvas').tap();
  for(let i=0;i<2;i++){await p.waitForTimeout(640);await p.locator('canvas').tap();}
  const flight=await p.evaluate(()=>window.__nightArcadeTest.snapshot());
  assert.ok(flight.started&&flight.pipes.length>0,'actually flying between pipes');
  assert.ok(flight.pipes.every(pipe=>pipe.gap>=115),'per-pipe gap is physical');
  await shot(p,'flappy-flight');
  await p.waitForFunction(()=>window.__nightArcadeTest.flags().finished,null,{timeout:7000,polling:70});
  await shot(p,'flappy-loss');
  await p.locator('#restart').click();
  assert.equal(await p.evaluate(()=>window.__nightArcadeTest.snapshot().started),false);
  assert.equal(await p.evaluate(()=>window.__nightArcadeTest.snapshot().pipes.length),0);
  // Play by observing the live position/velocity and issuing physical
  // taps, not by modifying game state. Demonstrate at least one valid pass.
  await p.locator('canvas').tap();
  let passed=false,flaps=1,maxSpeed=2.4,lastPosition=null;
  for(let i=0;i<240;i++){
   const bird=await p.evaluate(()=>window.__nightArcadeTest.snapshot());
   const score=await p.evaluate(()=>window.__nightArcadeTest.flags().points);
   maxSpeed=Math.max(maxSpeed,bird.speed||2.4);
   if(score>=1){passed=true;lastPosition=bird;break;}
   if(await p.evaluate(()=>window.__nightArcadeTest.flags().finished)){lastPosition=bird;break;}
   const pipe=bird.pipes.find(pipe=>pipe.x+62>bird.x-12);
   const canvasHeight=await p.locator('canvas').evaluate(c=>c.height);
   const target=pipe?pipe.y:(canvasHeight-42)/2;
   // Estimate where the falling bird will be ~eight frames ahead. Flap
   // early enough to stay inside the physical gap, even on short boards.
   const forecast=bird.y+Math.max(0,bird.vy)*7;
   if((forecast>target+8&&bird.vy>-1)||(bird.y>target+62&&bird.vy>-.8)){
    await p.locator('canvas').tap();flaps++;
   }
   await p.waitForTimeout(60);
  }
  if(passed)await shot(p,'flappy-scored');
  console.log('AGENT4_FLAPPY_SCORE_PLAY='+JSON.stringify({passed,flaps,maxSpeed,position:lastPosition&&{y:lastPosition.y,vy:lastPosition.vy,distance:lastPosition.distance}}));
  assert.equal(passed,true,'At least one pipe must be passed by real touch inputs');
  console.log('AGENT4_FLAPPY='+JSON.stringify({speed:flight.speed,gap:flight.pipes[0].gap,loss:true,restart:true,scored:true}));await p.close();
 }
 assert.deepEqual(errors,[]);
 console.log('PASS Agent4 interaction: bricks, shooter, wing hop with pointer gameplay, loss, restart and 5 viewports.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
