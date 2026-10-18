import assert from 'node:assert/strict';
import {chromium} from 'playwright';

const base=process.env.JIGSAW_URL||'https://night-play-combined-preview.vercel.app/jigsaw/';
const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox','--disable-dev-shm-usage']});
try{
 const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:3,isMobile:true,hasTouch:true});
 const errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
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
}finally{await browser.close()}
