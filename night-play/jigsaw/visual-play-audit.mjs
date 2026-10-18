import assert from 'node:assert/strict';
import {chromium} from 'playwright';
import {createServer} from 'node:http';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const server=createServer((req,res)=>{
 let p=new URL(req.url,'http://localhost').pathname.replace(/^[/]/,'');
 if(p==='jigsaw'||p==='jigsaw/')p='jigsaw/index.html';
 try{
  const file=readFileSync(path.join(root,p));
  const ct={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml'};
  res.writeHead(200,{'content-type':ct[path.extname(p)]||'text/plain'});res.end(file);
 }catch{res.writeHead(404);res.end();}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const base=process.env.JIGSAW_URL||'http://127.0.0.1:'+server.address().port+'/jigsaw/';
const picture=`<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600">
<defs><linearGradient id="sky" x2=".25" y2="1"><stop stop-color="#345779"/><stop offset=".7" stop-color="#aebfd0"/><stop offset="1" stop-color="#f5c98c"/></linearGradient>
<linearGradient id="lake" x2="0" y2="1"><stop stop-color="#639bab"/><stop offset="1" stop-color="#1b455d"/></linearGradient></defs>
<path fill="url(#sky)" d="M0 0H800V600H0Z"/><circle fill="#fff0b0" cx="560" cy="142" r="75"/>
<path fill="#2c5364" d="M0 341 128 178 245 298 369 99 485 321 626 184 800 380V600H0Z"/>
<path fill="#416b6c" d="M0 391 182 269 302 345 430 189 530 360 681 293 800 407V600H0Z"/>
<path fill="url(#lake)" d="M0 411Q205 381 400 411T800 409V600H0Z"/>
<path fill="#d8dfcf" opacity=".5" d="M100 446H690V449H100ZM30 473H740V476H30ZM140 530H800V532H140Z"/>
<g fill="#183d31"><path d="M15 448 48 235 89 449ZM80 470 132 170 182 470ZM680 472 725 216 770 472Z"/></g>
<g fill="#b4aa70" opacity=".8"><circle cx="205" cy="408" r="19"/><circle cx="223" cy="418" r="27"/><circle cx="600" cy="406" r="25"/></g>
</svg>`;

const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox','--disable-dev-shm-usage']});
try{
 const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:3,isMobile:true,hasTouch:true});
 const errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 await page.route('https://images.unsplash.com/**',route=>route.fulfill({status:200,contentType:'image/svg+xml',body:picture,headers:{'access-control-allow-origin':'*'}}));
 await page.goto(base,{waitUntil:'domcontentloaded',timeout:45000});
 await page.locator('[data-theme="photo-alpine-lake"]').click();
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound,null,{timeout:12000});
 await page.waitForTimeout(450);
 const shot=async label=>{
  const encoded=(await page.screenshot({type:'jpeg',quality:48})).toString('base64');
  console.log('JIGSAW_VISUAL_'+label+'='+encoded);
 };
 await shot('READY_24');
 const cdp=await page.context().newCDPSession(page);
 for(const pieces of [24,144]){
  if(pieces===144){
   await page.locator('#hudMenuButton').click();
   await page.locator('#hudSettingsButton').click();
   await page.locator('[data-pieces="144"]').click();
   await page.waitForFunction(()=>window.__nightJigsawTest.snapshot().pieces.length===144);
  }
  const layout=await page.evaluate(()=>{
   const state=window.__nightJigsawTest.snapshot(),r=document.getElementById('puzzleCanvas').getBoundingClientRect();
   const sx=r.width/state.viewport.w,sy=r.height/state.viewport.h;
   const candidates=state.pieces.filter(p=>!p.placed).map(p=>({id:p.id,
    x:r.left+(p.x+state.board.pieceWidth/2)*sx,
    y:r.top+(p.y+state.board.pieceHeight/2)*sy}))
    .filter(p=>p.x>30&&p.x<innerWidth-30&&p.y>90&&p.y<innerHeight-45);
   const item=candidates[candidates.length-1];
   const drag=document.getElementById('dragCanvas');
   return {item,viewport:state.viewport,dpr:state.pixelRatio,board:state.board,
    overlay:{width:drag.width,height:drag.height,cssWidth:getComputedStyle(drag).width,
     cssHeight:getComputedStyle(drag).height},count:state.pieces.length};
  });
  assert.ok(layout.item,'Loose piece should be on-screen to play: '+JSON.stringify(layout));
  console.log('JIGSAW_VISUAL_LAYOUT_'+pieces+'='+JSON.stringify(layout));
  const p=layout.item,dx=p.x<180?115:-110,dy=p.y>560?-85:85;
  const touch=(type,x,y)=>cdp.send('Input.dispatchTouchEvent',{
    type,touchPoints:type==='touchEnd'?[]:[{id:81,x,y,force:.9,radiusX:7,radiusY:7}]});
  await touch('touchStart',p.x,p.y);
  await page.waitForFunction(()=>window.__nightJigsawTest.snapshot().selected!==null,null,{timeout:5000});
  const before=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
  await shot('PICKUP_'+pieces);
  await page.evaluate(()=>{
    window.__nightFrameIntervals=[];let last=0;window.__nightFrameActive=true;
    function loop(t){if(!window.__nightFrameActive)return;
      if(last)window.__nightFrameIntervals.push(t-last);last=t;requestAnimationFrame(loop)}
    requestAnimationFrame(loop);
  });
  for(let step=1;step<=35;step++){
    const t=step/35;
    await touch('touchMove',p.x+dx*t,p.y+dy*t);
    if(step===17)await shot('MIDDRAG_'+pieces);
    await new Promise(resolve=>setTimeout(resolve,12));
  }
  const mid=await page.evaluate(()=>{
    window.__nightFrameActive=false;
    const a=window.__nightFrameIntervals.slice(1);
    const s=window.__nightJigsawTest.snapshot();
    const overlay=document.getElementById('dragCanvas');
    return {selected:s.selected,staticPaints:s.staticPaints,dragPaints:s.dragPaints,
       transform:overlay.style.transform,backing:[overlay.width,overlay.height],
       frameIntervals:a,slowFrames:a.filter(t=>t>25).length,
       p95:a.slice().sort((x,y)=>x-y)[Math.floor(a.length*.95)]||null};
  });
  console.log('JIGSAW_VISUAL_METRICS_'+pieces+'='+JSON.stringify({
   start:before.selected,staticPaintDelta:mid.staticPaints-before.staticPaints,
   dragPaintDelta:mid.dragPaints-before.dragPaints,slowFrames:mid.slowFrames,
   frameCount:mid.frameIntervals.length,frameP95:mid.p95,backing:mid.backing,
   transform:mid.transform,selected:mid.selected}));
  await shot('ENDDRAG_'+pieces);
  await touch('touchEnd',0,0);
  await page.waitForTimeout(200);
  await shot('RELEASE_'+pieces);
 }
 assert.deepEqual(errors,[],'No page JS exceptions');
 await cdp.detach();
 await page.close();
}finally{await browser.close();await new Promise(done=>server.close(done))}
