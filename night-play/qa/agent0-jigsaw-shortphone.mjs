/**
 * Agent Zero's additional 144-piece Jigsaw short-phone and touch-acquisition gate.
 *
 * Run: npm i --no-save playwright@1.56.1
 *      npx playwright install chrome
 *      node night-play/qa/agent10-jigsaw-fingertip.mjs
 *
 * This probes genuine Chromium touch selection, not solved-piece fixtures.
 * Selecting the wrong piece is a quality FAIL even if the page never crashes.
 * It does NOT certify finger-drag accuracy, puzzle completion or real Android.
 */
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const siteRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const evidenceDir = path.join(siteRoot, 'visual-output', 'agent10-fingertip');
mkdirSync(evidenceDir, { recursive: true });

const mimes = {
  '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml',
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png',
  '.webp': 'image/webp'
};
const server = createServer((request, response) => {
  let relative = new URL(request.url || '/', 'http://localhost').pathname.replace(/^\//, '');
  if (relative === 'jigsaw' || relative === 'jigsaw/') relative = 'jigsaw/index.html';
  try {
    const file = readFileSync(path.join(siteRoot, relative));
    response.writeHead(200, { 'content-type': mimes[path.extname(relative)] || 'application/octet-stream' });
    response.end(file);
  } catch {
    response.writeHead(404);
    response.end('Not found');
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const siteURL = 'http://127.0.0.1:' + server.address().port;
const offsets = [{dx:0,dy:0},{dx:10,dy:0},{dx:-10,dy:0},{dx:0,dy:10},{dx:0,dy:-10}];
const sampleCount = 26;
const results = [];
let browser;
try {
  browser = await chromium.launch({ headless: true, channel: 'chrome', args: ['--no-sandbox'] });
  for (const [width, height] of [[320,568],[390,740]]) {
    const page = await browser.newPage({ viewport: {width,height}, hasTouch:true, isMobile:true });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    try {
      await page.goto(siteURL + '/jigsaw/');
      await page.locator('[data-theme="photo-alpine-lake"]').click();
      await page.waitForFunction(() => window.__nightJigsawTest?.snapshot().activeRound, null, {timeout:10000});
      await page.locator('#hudMenuButton').click();
      await page.locator('#hudSettingsButton').click();
      await page.locator('[data-pieces="144"]').click();
      await page.waitForTimeout(170);

      const initial = await page.evaluate(() => {
        const state = window.__nightJigsawTest.snapshot();
        const canvas = document.querySelector('#puzzleCanvas');
        const rect = canvas.getBoundingClientRect();
        const scaleX = rect.width / canvas.width;
        const scaleY = rect.height / canvas.height;
        const candidates = state.pieces.filter(piece => !piece.placed).map(piece => ({
          id: piece.id,
          x: rect.left + (piece.x + state.board.pieceWidth / 2) * scaleX,
          y: rect.top + (piece.y + state.board.pieceHeight / 2) * scaleY
        })).filter(piece => piece.x>7 && piece.x<innerWidth-7 && piece.y>52 && piece.y<innerHeight-55);
        return { candidates, pieces:state.pieces.length,
          pieceWidth:state.board.pieceWidth, pieceHeight:state.board.pieceHeight };
      });

      const cdp = await page.context().newCDPSession(page);
      const attempts = [];
      for (const offset of offsets) {
        for (let i=0; i<Math.min(sampleCount, initial.candidates.length); i++) {
          const target = initial.candidates[Math.floor(
            (i + .5) * initial.candidates.length / Math.min(sampleCount, initial.candidates.length)
          )];
          const x = target.x + offset.dx;
          const y = target.y + offset.dy;
          if (x<4 || x>width-4 || y<42 || y>height-10) continue;
          await cdp.send('Input.dispatchTouchEvent', {
            type:'touchStart', touchPoints:[{x,y,id:100+attempts.length}]
          });
          await page.waitForTimeout(38);
          const selected = await page.evaluate(() => window.__nightJigsawTest.snapshot().selected);
          await cdp.send('Input.dispatchTouchEvent', { type:'touchEnd', touchPoints:[] });
          attempts.push({ target:target.id, picked:selected?.id || null,
            dx:offset.dx, dy:offset.dy, exact: selected?.id === target.id });
        }
      }

      const correct = attempts.filter(attempt => attempt.exact).length;
      const breakdown = offsets.map(({dx,dy}) => {
        const group=attempts.filter(attempt=>attempt.dx===dx && attempt.dy===dy);
        return {dx,dy,tries:group.length,correct:group.filter(attempt=>attempt.exact).length};
      });
      const result = {width,height,pieceCount:initial.pieces,pieceWidth:initial.pieceWidth,
        pieceHeight:initial.pieceHeight,tries:attempts.length,correct,
        accuracy:correct/attempts.length,breakdown,errors,
        wrongExamples:attempts.filter(attempt=>!attempt.exact).slice(0,15)};
      await page.screenshot({path:path.join(evidenceDir,'jigsaw-144-'+width+'.jpg'),type:'jpeg',quality:76});
      console.log('A10_FINGER_ACQUISITION '+JSON.stringify(result));
      results.push(result);
    } finally {
      await page.close();
    }
  }
} finally {
  await browser?.close();
  await new Promise(resolve=>server.close(resolve));
}
writeFileSync(path.join(evidenceDir,'results.json'), JSON.stringify({
  testedSource:process.env.GITHUB_SHA || 'local checkout HEAD',
  results, requiredAccuracy:.97
},null,2));
if (results.length!==2 || results.some(result=>result.pieceCount!==144
  || result.tries<100 || result.errors.length>0 || result.accuracy<.97)) {
  console.error('A10 QUALITY FAIL: 144-piece finger pickup is not reliably accurate at mobile size.');
  process.exitCode=1;
}
