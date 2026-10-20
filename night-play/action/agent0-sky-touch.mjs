import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const assets={'/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const file=assets[new URL(req.url,'http://127.0.0.1').pathname];
 if(!file){res.writeHead(404);res.end('Not found');return;}
 res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const base='http://127.0.0.1:'+server.address().port+'/action/?game=jump';
const dir=path.join(root,'visual-output','agent0-sky');
mkdirSync(dir,{recursive:true});
let browser;
const cases=[];
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 for(const width of [320,390,412]){
  const page=await browser.newPage({viewport:{width,height:844},isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(String(e)));
  await page.addInitScript(()=>{Math.random=()=>.5});
  await page.goto(base);
  await page.locator('#overlayStart').click();
  const snap=()=>page.evaluate(()=>window.__nightActionTest.snapshot());
  const box=await page.locator('#screen').boundingBox();
  const p=(x,y)=>({x:box.x+x/390*box.width,y:box.y+y/550*box.height,id:1});
  const cdp=await page.context().newCDPSession(page);
  const first=(await snap()).player.x;
  // Repro: tap far from player, keep finger planted. No involuntary steering.
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[p(340,300)]});
  await page.waitForTimeout(250);
  const planted=(await snap()).player.x;
  assert.ok(Math.abs(planted-first)<8,'P1 tap-and-hold must not move the jumper: '+JSON.stringify({width,first,planted}));
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  // Swipe from same right edge, 100px left. Move relative to x=180,
  // not teleport towards the screen x coordinate of the finger.
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[p(340,300)]});
  await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[p(240,300)]});
  await page.waitForTimeout(230);
  const swiped=(await snap()).player.x;
  assert.ok(swiped<first-28,'Swiping left must move left relative to starting player: '+JSON.stringify({width,first,swiped}));
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  await page.waitForTimeout(80);
  const afterLift=(await snap()).player.x;
  // New touch at opposite far edge and tiny involuntary movement: stable.
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[p(30,320)]});
  await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[p(34,320)]});
  await page.waitForTimeout(180);
  const jitter=(await snap()).player.x;
  assert.ok(Math.abs(jitter-afterLift)<16,'4px jitter must not cause player to chase new finger position: '+JSON.stringify({width,afterLift,jitter}));
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  // Keyboard input still works after canvas pointer, and is promptly released.
  await page.keyboard.down('ArrowRight');
  const beforeKey=(await snap()).player.x;
  await page.waitForTimeout(160);
  const afterKey=(await snap()).player.x;
  await page.keyboard.up('ArrowRight');
  assert.ok(afterKey>beforeKey+5,'Keyboard movement preserved after mobile gestures');
  assert.deepEqual(errors,[],'No JS exceptions');
  writeFileSync(path.join(dir,'sky-jumper-'+width+'.png'),await page.screenshot({fullPage:true}));
  cases.push({width,heldTapDelta:Math.round(planted-first),swipeDelta:Math.round(swiped-first),
   jitterDelta:Math.round(jitter-afterLift),keyDelta:Math.round(afterKey-beforeKey)});
  await page.close();
 }
 console.log('PASS Agent 0 Sky Jumper actual Chrome155 CDP mobile touch, 3 viewports, no tap-teleport, relative swipe, jitter, keyboard',JSON.stringify(cases));
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
