/**
 * Agent 10's independent, source-exact 144-piece Jigsaw acquisition audit.
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

const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
try{
 for(const [width,height] of [[320,700],[390,844]]){
  const page=await browser.newPage({viewport:{width,height},isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(siteURL+'/jigsaw/');
  await page.locator('[data-theme="photo-alpine-lake"]').click();
  await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
  await page.locator('#hudMenuButton').click();
  await page.locator('#hudSettingsButton').click();
  await page.locator('[data-pieces="144"]').click();
  await page.waitForTimeout(170);
  const cdp=await page.context().newCDPSession(page);
  let succeeded=0,mismatches=0,intervals=[];
  for(let i=0;i<20;i++){
   const state=await page.evaluate(()=>{
    const state=window.__nightJigsawTest.snapshot(),canvas=document.querySelector('#puzzleCanvas'),r=canvas.getBoundingClientRect();
    const sx=r.width/canvas.width,sy=r.height/canvas.height;
    return {board:state.board,rect:{left:r.left,top:r.top},sx,sy,pieces:state.pieces.filter(p=>!p.placed)};
   });
   const candidates=state.pieces.map(p=>({id:p.id,x:state.rect.left+(p.x+state.board.pieceWidth/2)*state.sx,y:state.rect.top+(p.y+state.board.pieceHeight/2)*state.sy,
    px:p.x,py:p.y})).filter(p=>p.x>33&&p.x<width-33&&p.y>75&&p.y<height-75);
   if(candidates.length<20)throw new Error('Not enough visible source pieces to drag');
   const target=candidates[Math.floor(((i*7)%candidates.length))];
   const dx=i%2===0?21:-21,dy=i%3===0?18:-18,touchId=i+4;
   await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:target.x,y:target.y,id:touchId}]});
   await page.waitForTimeout(20);
   const picked=await page.evaluate(()=>window.__nightJigsawTest.snapshot().selected?.id);
   if(picked!==target.id){mismatches++;await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});continue;}
   const start=Date.now();
   for(let step=1;step<=8;step++){
    await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:target.x+dx*step/8,y:target.y+dy*step/8,id:touchId}]});
    await page.waitForTimeout(9);
   }
   const latest=await page.evaluate(id=>window.__nightJigsawTest.snapshot().pieces.find(p=>p.id===id),target.id);
   await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   const distance=Math.hypot(latest.x-target.px,latest.y-target.py);
   if(distance>10){succeeded++;intervals.push(Date.now()-start)}
   else throw new Error('Drag did not follow finger '+target.id+' '+distance);
  }
  await page.screenshot({path:path.join(evidenceDir,'jigsaw-144-drag-'+width+'.jpg'),type:'jpeg',quality:70});
  console.log('A0_JIGSAW_DRAGS '+JSON.stringify({width,height,succeeded,mismatches,meanMs:intervals.reduce((a,b)=>a+b,0)/Math.max(1,intervals.length),errors}));
  if(succeeded<15||mismatches>4||errors.length)process.exitCode=1;
  await page.close();
 }
}finally{await browser.close();await new Promise(ok=>server.close(ok));}
