import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

// Browser gameplay regressions: REAL input, collision, restart, five viewports.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const f=routes[new URL(req.url,'http://localhost').pathname];
 if(!f){res.writeHead(404);res.end('Missing');return;}
 res.writeHead(200,{'content-type':f.endsWith('.css')?'text/css':f.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,f)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
let browser;
const results=[],errors=[];
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const url='http://127.0.0.1:'+server.address().port;
 async function open(game,random,width=390){
  const p=await browser.newPage({viewport:{width,height:844},isMobile:width<600,hasTouch:true});
  p.on('pageerror',e=>errors.push(String(e)));
  await p.addInitScript(n=>{Math.random=()=>n},random);
  await p.goto(url+'/action/?game='+game);
  await p.locator('#overlayStart').click();
  return p;
 }
 const snap=p=>p.evaluate(()=>window.__nightActionTest.snapshot());
 const condition=(p,f)=>p.waitForFunction(f,null,{timeout:7000,polling:16});
 // Idle collision followed by actual restart.
 {
  const p=await open('runner',.18);
  await condition(p,()=>window.__nightActionTest.snapshot().over);
  assert.equal((await snap(p)).passed,0,'low flyer catches idle runner');
  await p.screenshot({path:path.join(output,'agent3-runner-collision.jpg')});
  await p.locator('#overlayStart').click();
  assert.equal((await snap(p)).over,false);assert.equal((await snap(p)).score,0);
  results.push('idle flyer hit and real restart');await p.close();
 }
 // Real keyboard jump at a live upcoming flying hazard.
 {
  const p=await open('runner',.18);
  await condition(p,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='flying'&&o.x<190));
  await p.keyboard.press('Space');
  assert.ok((await snap(p)).player.vy<0);
  await condition(p,()=>{const s=window.__nightActionTest.snapshot();return s.passed>=1||s.over});
  assert.equal((await snap(p)).over,false,'timed jump clears flyer');
  results.push('timed jump cleared flyer');await p.close();
 }
 // High gliders require duck; walking beneath them is not invulnerable.
 {
  const p=await open('runner',.4);
  await condition(p,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='glider'&&o.x<178));
  await p.keyboard.down('ArrowDown');
  await condition(p,()=>{const s=window.__nightActionTest.snapshot();return s.passed>=1||s.over});
  assert.equal((await snap(p)).over,false,'slide clears high glider');
  await p.keyboard.up('ArrowDown');
  results.push('slide cleared glider');await p.close();
 }
 // Touchscreen swipe down must not trigger the previous accidental jump.
 {
  const p=await open('runner',.18);
  const b=await p.locator('#screen').boundingBox(),x=b.x+b.width*.55,y=b.y+b.height*.55;
  const c=await p.context().newCDPSession(p);
  await c.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});
  await c.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x,y:y+65}]});
  await p.waitForTimeout(80);
  assert.equal((await snap(p)).player.duck,true,'mobile swipe down ducked');
  await c.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  assert.equal((await snap(p)).player.vy,0,'mobile swipe did not jump');
  await p.screenshot({path:path.join(output,'agent3-runner-touch-duck.jpg')});
  await p.waitForTimeout(700);
  assert.equal((await snap(p)).player.duck,false,'swipe duck expires');
  await p.touchscreen.tap(x,y);await p.waitForTimeout(60);
  assert.ok((await snap(p)).player.vy<0,'touchscreen tap jumps');
  results.push('touchscreen duck then tap jump');await p.close();
 }
 for(const width of [320,360,390,412,1280])for(const game of ['jump','runner']){
  const p=await open(game,game==='jump'?.5:.18,width);
  const state=await snap(p);
  if(game==='jump'){
   assert.ok(state.platforms.slice(0,5).every(q=>!q.moving&&q.w>=82),'five generous safe opening platforms');
   assert.ok(state.platforms.every(q=>q.ttl===undefined&&q.fragile===undefined),'permanent platforms');
   assert.equal(state.hazardY,undefined,'no storm');
   assert.equal(await p.locator('#stat').isVisible(),false,'one height readout');
  }
  await p.waitForTimeout(300);
  const size=await p.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
  assert.ok(size.scroll<=size.width+2,'mobile/desktop overflow '+JSON.stringify({width,game,size}));
  await p.screenshot({path:path.join(output,`agent3-${game}-${width}.jpg`)});
  results.push(game+' rendered '+width);
  await p.close();
 }
 assert.deepEqual(errors,[]);
 console.log('NIGHT_AGENT3_PLAY='+JSON.stringify(results));
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
