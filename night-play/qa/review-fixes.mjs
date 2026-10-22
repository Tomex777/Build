// Isolated source gameplay: real inputs, five viewports, finite racing outcomes.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import {resolve,extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=fileURLToPath(new URL('../',import.meta.url)),out=root+'visual-output/review-fixes/';mkdirSync(out,{recursive:true});
const rewrites=JSON.parse(readFileSync(root+'vercel.json')).rewrites;
const server=createServer((req,res)=>{let path=new URL(req.url,'http://localhost').pathname;
 if(path==='/api/account'){res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({linked:false,profile:null}));return}
 path=rewrites.find(r=>r.source===path)?.destination||path;if(path.endsWith('/'))path+='index.html';const file=resolve(root,'.'+path);if(!file.startsWith(root)){res.writeHead(403);res.end();return}try{res.writeHead(200,{'content-type':({'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.jpg':'image/jpeg','.png':'image/png'})[extname(file)]||'application/octet-stream'});res.end(readFileSync(file))}catch{res.writeHead(404);res.end()}});
await new Promise(done=>server.listen(0,'127.0.0.1',done));const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const snap=p=>p.evaluate(()=>window.__nightRacingTest.snapshot());
async function steer(page,x){const b=await page.locator('#track').boundingBox(),sx=b.x+b.width*(83+x*254)/420,sy=b.y+b.height*.76;await page.touchscreen.tap(sx,sy)}
try{
 for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[1280,900]]){
  const p=await browser.newPage({viewport:{width,height},hasTouch:true,isMobile:width<700}),errors=[];p.on('pageerror',e=>errors.push(e.message));
  await p.goto(base+'/racing/');await p.getByRole('button',{name:'Start race',exact:true}).tap();await steer(p,.36);
  await p.waitForFunction(()=>window.__nightRacingTest.snapshot().travel>30);
  const before=await snap(p);assert.ok(Math.abs(before.x-.36)<.06,'touch steering follows target');
  await p.getByRole('button',{name:'Pause race',exact:true}).tap();const paused=await snap(p);await p.waitForTimeout(200);assert.equal((await snap(p)).travel,paused.travel,'pause freezes race');
  await p.screenshot({path:out+'race-paused-'+width+'.png',fullPage:true});await p.getByRole('button',{name:'Resume race',exact:true}).tap();await p.waitForFunction(d=>window.__nightRacingTest.snapshot().travel>d,paused.travel+10);
  const layout=await p.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));assert.ok(layout.scroll<=width+1,'no horizontal clipping '+width);
  if(width===390){
   const brake=await p.locator('#brake').boundingBox(),cdp=await p.context().newCDPSession(p);const speed=(await snap(p)).velocity;
   await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:3,x:brake.x+brake.width/2,y:brake.y+brake.height/2}]});await p.waitForTimeout(750);await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});assert.ok((await snap(p)).velocity<speed,'real held brake slows the vehicle');await cdp.detach();
   let current=await snap(p),start=Date.now();while(current.state==='running'&&Date.now()-start<45000){const near=current.rivals.filter(r=>Math.abs(r.d-current.travel)<90);const lanes=[.1,.37,.64,.9];const lane=lanes.sort((a,b)=>Math.min(...near.map(r=>Math.abs(r.x-b)),1)-Math.min(...near.map(r=>Math.abs(r.x-a)),1))[0];await steer(p,lane);await p.waitForTimeout(250);current=await snap(p)}
   assert.equal(current.state,'finished','finite race finishes');assert.ok(current.travel>=2500,'reaches real finish line');assert.equal(await p.locator('#coverTitle').textContent(),'First over the line!');assert.equal(await p.evaluate(()=>localStorage.getItem('night-play-history-count-v1-racing')),'1','finished race appears in Profile activity');await p.screenshot({path:out+'race-won-390.png',fullPage:true});
   await p.getByRole('button',{name:'Race again',exact:true}).tap();assert.ok((await snap(p)).travel<20,'restart resets actual race');
   start=Date.now();current=await snap(p);while(current.state==='running'&&Date.now()-start<55000){const target=current.rivals.filter(r=>r.d>current.travel-15).sort((a,b)=>a.d-b.d)[0]||current.rivals[0];await steer(p,target.x);await p.waitForTimeout(100);current=await snap(p)}
   assert.equal(current.crashes,3,'actual car impacts end the race');assert.equal(await p.evaluate(()=>localStorage.getItem('night-play-history-count-v1-racing')),'2','replay records one additional round');assert.equal(await p.locator('#coverTitle').textContent(),'Race over.');await p.screenshot({path:out+'race-lost-390.png',fullPage:true});
  }
  for(const path of ['/multiplayer/ttt/','/draw/','/puzzles/?game=sudoku']){await p.goto(base+path);assert.equal(await p.locator('.night-shell-header').count(),1,'one shared header '+path);assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'no overflow '+path+' '+width)}
  await p.getByRole('button',{name:'Game settings',exact:true}).tap();assert.equal(await p.locator('#settingsClose svg').count(),1);await p.getByRole('button',{name:'Close settings',exact:true}).tap();assert.equal(await p.locator('#settingsDialog').isVisible(),false,'SVG close really closes settings');
  assert.deepEqual(errors,[],'no browser exceptions '+width);console.log('PASS review fixes viewport '+width);await p.close();
 }
}finally{await browser.close();await new Promise(done=>server.close(done))}
