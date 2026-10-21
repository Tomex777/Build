import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

// Run against ACTUAL NIGHT Play action route, with real Chromium pointer/touch.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'visual-output');
mkdirSync(output, { recursive:true });
const assets = {'/action/':'action/index.html','/action/style.css':'action/style.css',
  '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css','/action/art/flyer-up.svg':'action/art/flyer-up.svg','/action/art/flyer-down.svg':'action/art/flyer-down.svg','/action/art/glider.svg':'action/art/glider.svg','/action/art/pillar.svg':'action/art/pillar.svg','/action/art/stone.svg':'action/art/stone.svg','/action/art/barrel.svg':'action/art/barrel.svg'};
const server = createServer((req,res) => {
  const route = new URL(req.url,'http://127.0.0.1').pathname, file = assets[route];
  if (!file) { res.writeHead(404); res.end('Missing '+route); return; }
  res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':file.endsWith('.svg')?'image/svg+xml':'text/html'});
  res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
let browser;
const ledger = [], pageErrors = [];
const snap = page => page.evaluate(() => window.__nightActionTest.snapshot());
const waitState = (page,fn,timeout=8000) => page.waitForFunction(fn,null,{timeout,polling:16});
const screenshot = async (page,name) =>
  page.screenshot({path:path.join(output,'agent3-wave2-'+name+'.jpg'),type:'jpeg',quality:80});
const url = 'http://127.0.0.1:'+server.address().port;

async function open(game,seed,width=390) {
  const page = await browser.newPage({viewport:{width,height:844},isMobile:width<=412,hasTouch:true,
    reducedMotion:'reduce'});
  page.on('pageerror',e => pageErrors.push(String(e)));
  await page.addInitScript(n => { Math.random = () => n; },seed);
  await page.goto(url+'/action/?game='+game);
  await page.locator('#overlayStart').click();
  return page;
}
async function touch(page,kind,points) {
  const c = await page.context().newCDPSession(page);
  await c.send('Input.dispatchTouchEvent',{type:kind,touchPoints:points});
  return c;
}
const xy = (box,x,y) => ({x:box.x + x/390*box.width,y:box.y+y/550*box.height});

try {
  browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});

  // Regression P1: holding a completed down-swipe is a held SLIDE, not a
  // one-shot 580ms dodge that stands up with the finger still on the glass.
  {
    const page=await open('runner',.4);
    const box=await page.locator('#screen').boundingBox();
    const client=await page.context().newCDPSession(page);
    await client.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[xy(box,180,230)]});
    await client.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[xy(box,180,330)]});
    assert.equal((await snap(page)).player.duck,true,'down-swipe begins slide');
    await page.waitForTimeout(840);
    await screenshot(page,'held-slide');
    assert.equal((await snap(page)).player.duck,true,
      'P1: a finger HELD down after sliding must not auto-stand mid-obstacle');
    await client.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await page.waitForTimeout(80);
    assert.equal((await snap(page)).player.duck,false,'lift ends held slide');
    ledger.push({case:'slide-hold',result:'PASS',durationMs:840});
    await page.close();
  }

  // Real glider: wait for the physical obstacle, swipe/hold through its
  // complete crossing, then verify survival and actual pass counter.
  {
    const page=await open('runner',.4),box=await page.locator('#screen').boundingBox();
    await waitState(page,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='glider'&&o.x<224));
    const c=await page.context().newCDPSession(page);
    await c.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[xy(box,175,225)]});
    await c.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[xy(box,175,325)]});
    await waitState(page,()=>{const s=window.__nightActionTest.snapshot();return s.over||s.passed>=1},3000);
    await screenshot(page,'glider-crossing');
    const result=await snap(page);
    assert.equal(result.over,false,'held mobile swipe must survive a genuinely crossing high glider');
    assert.ok(result.passed>=1,'glider left playfield through actual frame physics');
    await c.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    ledger.push({case:'glider-held-survival',result:'PASS',passed:result.passed});
    await page.close();
  }

  // Up-swipe/tap: actual touch input launches the jump (no fake test-state
  // mutation). The low flyer remains lethal if a user does nothing.
  {
    const page=await open('runner',.18);
    await waitState(page,()=>window.__nightActionTest.snapshot().obstacles.some(o=>o.kind==='flying'&&o.x<189));
    const box=await page.locator('#screen').boundingBox();
    const c=await page.context().newCDPSession(page);
    await c.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[xy(box,175,330)]});
    await c.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[xy(box,175,230)]});
    assert.ok((await snap(page)).player.vy<0,'up-swipe jumps without waiting for release');
    await c.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await waitState(page,()=>{const s=window.__nightActionTest.snapshot();return s.passed>=1||s.over},3500);
    const result=await snap(page);
    await screenshot(page,'timed-jump');
    assert.equal(result.over,false,'timely real touch clears low flying enemy');
    ledger.push({case:'flying-touch-survival',result:'PASS',passed:result.passed});
    await page.close();
  }

  // Genuine idle death, unearned score prevention, stored personal best
  // and restarting a round from the OVER overlay.
  {
    const page=await open('runner',.18);
    await waitState(page,()=>window.__nightActionTest.snapshot().over,7000);
    const result=await snap(page);
    assert.equal(result.passed,0,'idle runner cannot ghost through a low-flying enemy');
    assert.ok(result.score>0,'running accumulates distance before losing');
    const stored=await page.evaluate(()=>Number(localStorage.getItem('night-play-v1-action-runner')));
    assert.equal(stored,result.score,'score saved on actual death');
    await screenshot(page,'idle-death');
    await page.locator('#overlayStart').click();
    const replay=await snap(page);
    assert.equal(replay.over,false,'restart clears game over');
    assert.equal(replay.score,0,'restart clears current run score');
    assert.equal(await page.locator('#best').textContent(),String(stored),'best is retained on replay');
    ledger.push({case:'runner-death-restart-best',result:'PASS',best:stored});
    await page.close();
  }

  // The actual Doodle-style Sky Jumper must bounce on a real permanent
  // opening platform and respond while touch is held, at phone/desktop sizes.
  for (const width of [320,360,390,412,1280]) {
    const page=await open('jump',.5,width),initial=await snap(page);
    assert.ok(initial.platforms.slice(0,5).every(o=>!o.moving&&o.w>=82),
      'opening five platforms must remain stable');
    const platform0=initial.platforms[0];
    await waitState(page,()=>{const s=window.__nightActionTest.snapshot();return s.player.vy>50},1800);
    await waitState(page,()=>{const s=window.__nightActionTest.snapshot();return s.player.vy< -500&&s.elapsed>.7},3000);
    const bounced=await snap(page);
    assert.ok(bounced.platforms.some(o=>Math.abs(o.baseX-platform0.baseX)<1),
      'landing does not erase the first platform');
    const board=await page.locator('#screen').boundingBox();
    const c=await page.context().newCDPSession(page);
    await c.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[xy(board,230,270)]});
    await c.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[xy(board,370,270)]});
    const before=(await snap(page)).player.x;
    await page.waitForTimeout(210);
    const after=(await snap(page)).player.x;
    assert.ok(after>before+10,'held horizontal touch drag steers player on-screen');
    await c.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
    assert.ok(overflow<=2,'no horizontal overflow at '+width+'px');
    await screenshot(page,'sky-jumper-'+width);
    ledger.push({case:'sky-jumper-touch-and-bounce',width,result:'PASS',deltaX:after-before});
    await page.close();
  }
  assert.deepEqual(pageErrors,[],'no browser page exceptions');
} catch(error) {
  ledger.push({case:'FAILED',message:String(error),stack:String(error?.stack||'')});
  throw error;
} finally {
  writeFileSync(path.join(output,'agent3-wave2-results.json'),JSON.stringify(ledger,null,2));
  console.log('NIGHT_AGENT3_WAVE2='+JSON.stringify(ledger));
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
