/**
 * NIGHT Play A10 catalog entry gate. Audits every LIVE Games tile in real
 * Chromium at five device sizes. A successful launch is NOT a completed match.
 * In particular an anonymous local server cannot certify production rooms.
 *
 * Run: npm i --no-save playwright@1.56.1
 *      npx playwright install chromium
 *      node night-play/qa/agent10-catalog-gate.mjs
 */
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFileSync, statSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'visual-output', 'agent10-catalog');
mkdirSync(output, { recursive: true });
const sizes = [[320, 700], [360, 800], [390, 844], [412, 915], [1280, 900]];
const types = { '.html':'text/html', '.js':'text/javascript', '.mjs':'text/javascript',
  '.css':'text/css', '.svg':'image/svg+xml', '.png':'image/png',
  '.jpg':'image/jpeg', '.jpeg':'image/jpeg', '.webp':'image/webp',
  '.json':'application/json', '.woff2':'font/woff2', '.mp3':'audio/mpeg' };
const apiNote = 'Anonymous isolated source server. Authentication and remote multiplayer are NOT tested.';
const server = createServer((req, res) => {
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); }
  catch { res.writeHead(400); res.end('Invalid path'); return; }
  if (pathname.startsWith('/api/')) {
    res.writeHead(401, {'content-type':'application/json'});
    res.end(JSON.stringify({error:apiNote})); return;
  }
  if (['/','/games','/welcome','/dashboard','/profile','/updates','/terms','/privacy'].includes(pathname)
      || /^\/play\/(snake|2048|ttt)$/.test(pathname)) pathname='/index.html';
  else if (pathname.endsWith('/')) pathname+='index.html';
  const filename = path.resolve(root, '.'+pathname);
  if (!filename.startsWith(root+path.sep)) {
    res.writeHead(403); res.end('Forbidden'); return;
  }
  try {
    if (!statSync(filename).isFile()) throw Error('Not a file');
    res.writeHead(200, {'content-type':types[path.extname(filename)]||'application/octet-stream'});
    res.end(readFileSync(filename));
  } catch {
    res.writeHead(404, {'content-type':'text/plain'});
    res.end('NIGHT QA: missing route or asset '+pathname);
  }
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const base = 'http://127.0.0.1:'+server.address().port;
const report = { baseline:process.env.GITHUB_SHA||'local checkout (SHA not provided)',
  source:'local checked-out night-play/', browser:null, startedAt:new Date().toISOString(),
  viewports:sizes.map(([width,height])=>({width,height})), inventory:[],
  tests:[], releaseDecision:'NO_GO', limitations:[
    'Anonymous local source checkout: verified WhatsApp identity and real remote rooms BLOCKED.',
    'Launch, interaction and screenshot are not win/loss/full-match proofs.',
    'Physical Android touch latency and subjective visual sign-off NOT TESTED.'
  ] };
let browser;
function infer(item) {
  if(item.href) return item.href;
  if(item.external) return item.external;
  if(item.arcade) return '/arcade/?game='+encodeURIComponent(item.arcade);
  if(item.club) return '/club/?game='+encodeURIComponent(item.club);
  if(item.jigsaw) return '/jigsaw/';
  if(item.game) return '/play/'+item.game;
  return null;
}
async function actualTouch(page, box) {
  const session = await page.context().newCDPSession(page);
  const x=box.x+box.width*.5, y=box.y+box.height*.5;
  try {
    await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:7,x,y}]});
    await session.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{id:7,x:x+Math.min(35,box.width*.15),y:y-12}]});
    await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  } finally { await session.detach(); }
}
async function interaction(page, item, touch) {
  if(item.href?.includes('/party/') || item.href==='/stories/') return 'NOT_TESTED: remote authorization or story full-play';
  if(item.game==='ttt') {
    const target=page.locator('.tttboard button:not([disabled])').first();
    await target.waitFor({timeout:4000});
    touch?await target.tap():await target.click();
    return 'REAL_INPUT: first tic-tac-toe square tapped (round not completed)';
  }
  if(item.game==='2048') {
    await page.keyboard.press('ArrowLeft');
    return 'REAL_INPUT: arrow-key slide sent (win/loss not tested)';
  }
  if(item.game==='snake') {
    const canvas=page.locator('#snakeCanvas');
    await canvas.waitFor({timeout:4000});
    const box=await canvas.boundingBox();
    if(touch) await actualTouch(page,box);
    else await page.keyboard.press('ArrowDown');
    return 'REAL_INPUT: directional input sent (death/restart not tested)';
  }
  if(item.href?.includes('/action/')) {
    const start=page.locator('#overlayStart');
    await start.waitFor({timeout:4000});
    touch?await start.tap():await start.click();
    const canvas=page.locator('canvas:visible').first();
    if(touch) await actualTouch(page,await canvas.boundingBox());
    else await page.keyboard.press('Space');
    return 'REAL_INPUT: started action game and sent canvas/keyboard input; full session NOT TESTED';
  }
  if(item.href?.includes('/piano-tiles/') || item.external==='/piano-tiles/') {
    const start=page.locator('#coverAction');
    await start.waitFor({timeout:4000});
    touch?await start.tap():await start.click();
    await page.keyboard.press('1');
    return 'REAL_INPUT: piano started, lane 1 key pressed; melody finish NOT TESTED';
  }
  if(item.external==='/strategy/') {
    await page.locator('#chessBoard [data-square="e2"]').click();
    await page.locator('#chessBoard [data-square="e4"]').click();
    await page.locator('#moveList .move-row').first().waitFor({timeout:4000});
    return 'REAL_INPUT: legal white pawn e2-e4 confirmed in move history; mate NOT TESTED';
  }
  if(item.arcade==='bird') {
    touch?await page.locator('canvas.mode-bird').tap():await page.keyboard.press('Space');
    return 'REAL_INPUT: Wing Hop flap; distance/loss NOT TESTED';
  }
  if(item.arcade==='blocks') {
    const control=page.locator('[data-control="drop"]').first();
    await control.waitFor({timeout:4000});
    touch?await control.tap():await control.click();
    return 'REAL_INPUT: hard drop; full line-clearing match NOT TESTED';
  }
  if(item.arcade==='mines') {
    const tile=page.locator('#mineboard [data-tile]').first();
    await tile.waitFor({timeout:4000});
    touch?await tile.tap():await tile.click();
    return 'REAL_INPUT: first tile revealed; winning/losing board NOT TESTED';
  }
  if(item.arcade==='maze') {
    const key=page.locator('[data-control="down"]').first();
    await key.waitFor({timeout:4000});
    touch?await key.tap():await key.click();
    return 'REAL_INPUT: Maze Chase direction control; full level NOT TESTED';
  }
  if(item.href?.includes('game=memory')) {
    const tile=page.locator('[data-memory-card],.memory-card').first();
    if(await tile.count()){touch?await tile.tap():await tile.click();return 'REAL_INPUT: memory card tapped; 15 pairs NOT TESTED';}
  }
  if(item.href?.includes('game=connect')) {
    const slot=page.locator('[data-column],.connect-col').first();
    if(await slot.count()){touch?await slot.tap():await slot.click();return 'REAL_INPUT: connect column tapped; four-in-row NOT TESTED';}
  }
  return 'NOT_TESTED: no safe game-specific full-round probe yet';
}
try {
  browser = await chromium.launch({headless:true,args:['--no-sandbox']});
  report.browser=browser.version();
  const inventoryPage=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const home=await inventoryPage.goto(base+'/games',{waitUntil:'domcontentloaded'});
  assert.equal(home.status(),200,'games catalogue HTTP 200');
  await inventoryPage.locator('#libraryGrid .library-card[data-live="true"]').first().waitFor();
  report.inventory=await inventoryPage.locator('#libraryGrid .library-card[data-live="true"]').evaluateAll(nodes=>nodes.map((el,index)=>({
    index, name:el.querySelector('h3')?.textContent.trim()||el.getAttribute('aria-label')||'Unnamed',
    href:el.getAttribute('href'),game:el.dataset.game||null,external:el.dataset.externalGame||null,
    arcade:el.dataset.arcade||null,club:el.dataset.club||null,jigsaw:el.hasAttribute('data-jigsaw'),
    category:el.dataset.cat||'unknown'
  })));
  assert.ok(report.inventory.length>=25,'expected at least 25 playable/advertised tiles');
  assert.equal(new Set(report.inventory.map(x=>x.name)).size,report.inventory.length,'duplicated playable titles');
  for(const item of report.inventory) assert.ok(infer(item),'live tile missing route: '+item.name);
  await inventoryPage.screenshot({path:path.join(output,'catalog-390.jpg'),type:'jpeg',quality:75,fullPage:true});
  await inventoryPage.close();

  for(const [width,height] of sizes) {
    const mobile=width<600;
    const context=await browser.newContext({viewport:{width,height},hasTouch:mobile,isMobile:mobile,
      deviceScaleFactor:mobile?2:1,reducedMotion:'reduce'});
    for(const item of report.inventory) {
      const result={game:item.name,category:item.category,expectedRoute:infer(item),
        viewport:width+'x'+height,launch:'FAIL',interaction:'NOT_TESTED',fullRound:'NOT_TESTED',
        overflow:null,errors:[],warnings:[],screenshot:null};
      const page=await context.newPage();
      page.on('pageerror',err=>result.errors.push(String(err)));
      page.on('response',response=>{
        if(response.status()>=400 && /\.(js|mjs|css)(?:\?|$)/.test(response.url())
          && response.url().startsWith(base))result.errors.push('Asset '+response.status()+' '+response.url());
      });
      try {
        const landing=await page.goto(base+'/games',{waitUntil:'domcontentloaded',timeout:12000});
        assert.equal(landing.status(),200,'catalogue did not load');
        const card=page.locator('#libraryGrid .library-card[data-live="true"]').nth(item.index);
        assert.equal((await card.locator('h3').innerText()).trim(),item.name,'catalogue card changed under test');
        mobile?await card.tap({timeout:5000}):await card.click({timeout:5000});
        const target=new URL(base+result.expectedRoute);
        await page.waitForURL(url=>url.pathname===target.pathname && url.search===target.search,{timeout:6000});
        if(item.game) {
          await page.locator('#gameDialog[open]').waitFor({timeout:4000});
        } else {
          const heading=await page.locator('h1,h2').first().innerText({timeout:4000});
          assert.ok(heading.trim().length>0,'no visible heading');
        }
        const dimensions=await page.evaluate(()=>({
          rootWidth:document.documentElement.scrollWidth,bodyWidth:document.body.scrollWidth,
          viewport:window.innerWidth,bodyText:document.body.innerText.length
        }));
        result.overflow=Math.max(dimensions.rootWidth,dimensions.bodyWidth)-dimensions.viewport;
        assert.ok(dimensions.bodyText>30,'blank/empty destination');
        assert.ok(result.overflow<=3,'horizontal overflow '+result.overflow+'px');
        if(width===390 && !item.href?.includes('/party/')) {
          try { result.interaction=await interaction(page,item,mobile); }
          catch(err){ result.warnings.push('Interaction incomplete: '+String(err)); }
        } else if(item.href?.includes('/party/')) {
          result.interaction='BLOCKED: real signed host and server-side guests unavailable in anonymous source QA';
        }
        if(width===390) {
          const filename='live-'+String(item.index).padStart(2,'0')+'-'+item.name.toLowerCase().replace(/[^a-z0-9]+/g,'-')+'.jpg';
          await page.screenshot({path:path.join(output,filename),type:'jpeg',quality:70});
          result.screenshot=filename;
        }
        if(result.errors.length) throw Error('Uncaught JS or failed script: '+result.errors.join('; '));
        result.launch='PASS';
      } catch(err) {
        result.errors.push(String(err));
        try {
          if(width===390) {
            const filename='FAIL-'+String(item.index).padStart(2,'0')+'.jpg';
            await page.screenshot({path:path.join(output,filename),type:'jpeg',quality:65,timeout:2000});
            result.screenshot=filename;
          }
        } catch {}
      } finally {
        report.tests.push(result);
        console.log(result.launch+' '+item.name+' '+result.viewport+' '+result.interaction);
        await page.close();
      }
    }
    await context.close();
  }
  report.finishedAt=new Date().toISOString();
  report.counts={listed:report.inventory.length,checks:report.tests.length,
    routePass:report.tests.filter(r=>r.launch==='PASS').length,
    failed:report.tests.filter(r=>r.launch==='FAIL').length,
    touched:report.tests.filter(r=>r.interaction.startsWith('REAL_INPUT')).length,
    blockedOnline:report.tests.filter(r=>r.interaction.startsWith('BLOCKED')).length};
  console.log('NIGHT_A10_CATALOG_SUMMARY '+JSON.stringify(report.counts));
  if(report.counts.failed) process.exitCode=1;
} finally {
  writeFileSync(path.join(output,'catalog-results.json'),JSON.stringify(report,null,2));
  await browser?.close();
  await new Promise(resolve=>server.close(resolve));
}