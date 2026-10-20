
import {chromium} from 'playwright';import {mkdirSync,writeFileSync} from 'node:fs';
const B=(process.env.NIGHT_PLAY_BASE_URL||'https://night-play-combined-preview.vercel.app').replace(/\/$/,''),O=(process.env.NIGHT_QA_ARTIFACT_DIR||'night-play/visual-output/agent10');mkdirSync(O,{recursive:true});const cases=[];
const b=await chromium.launch({headless:true,args:['--no-sandbox']});
const test=async(name,cb)=>{try{let v=await cb();cases.push({name,pass:true,...v});console.log('PASS',name,JSON.stringify(v).slice(0,420))}catch(e){cases.push({name,pass:false,error:String(e)});console.log('FAIL',name,String(e).slice(0,700))}};
async function open(game,width=390,height=844){
 const ctx=await b.newContext({viewport:{width,height},hasTouch:width<=440,isMobile:width<=440}),p=await ctx.newPage(),errors=[];
 p.on('pageerror',e=>errors.push(String(e)));
 await p.route('**/arcade/games.js',async route=>{const res=await route.fetch(),src=await res.text(),guard='if(location.hostname==="localhost"||location.hostname==="127.0.0.1")';if(!src.includes(guard))throw Error('diagnostic guard missing');await route.fulfill({response:res,body:src.replace(guard,'if(true)')})});
 await p.goto(B+'/arcade/?game='+game);await p.locator(game==='mines'?'#mineboard [data-tile]':'canvas:visible').first().waitFor({timeout:10000});
 return {ctx,p,errors};
}
const shot=(p,name)=>p.screenshot({path:O+'/'+name+'.jpg',type:'jpeg',quality:75});
const snap=p=>p.evaluate(()=>window.__nightArcadeTest.snapshot());
try{
 await test('winghop-real-flights-and-restart',async()=>{
  const {ctx,p,errors}=await open('bird');
  try{
   let first=await snap(p);await shot(p,'wing-start');
   await p.locator('canvas.mode-bird').tap();
   await p.waitForTimeout(250);
   let flying=await snap(p);await shot(p,'wing-flight');
   await p.waitForFunction(()=>window.__nightArcadeTest.flags().finished,null,{timeout:6500,polling:50});
   let death=await snap(p),finish=await p.evaluate(()=>window.__nightArcadeTest.flags().finished);await shot(p,'wing-loss');
   await p.locator('#restart').tap();let restart=await snap(p);
   if(first.started||!flying.started||flying.distance<=0||!finish||restart.started||restart.pipes.length!==0||errors.length)throw Error(JSON.stringify({first,flying,death,restart,errors}).slice(0,500));
   return {flightDistance:flying.distance,deathY:death.y,dead:finish,restartClean:!restart.started};
  }finally{await ctx.close()}
 });
 await test('winghop-responsive-phone-desktop',async()=>{
  let captures=[];
  for(let [width,height] of [[320,568],[360,740],[390,844],[412,915],[900,450],[1280,800]]){
   const {ctx,p,errors}=await open('bird',width,height);
   try{
    const m=await p.locator('canvas.mode-bird').evaluate(el=>{const r=el.getBoundingClientRect(),button=document.querySelector('#restart').getBoundingClientRect();return {left:r.left,right:r.right,bottom:r.bottom,width:r.width,height:r.height,pageWidth:document.documentElement.scrollWidth,innerWidth,restartY:button.top}});
    captures.push({width,height,...m,errors});
    if(m.left<0||m.right>width+1||m.pageWidth>width+1||m.bottom>height+3||errors.length)throw Error(JSON.stringify(m));
    if(width===900)await shot(p,'wing-landscape');
   }finally{await ctx.close()}
  }
  return {captures};
 });
 await test('blockdrop-real-hard-drop-score',async()=>{
  const {ctx,p,errors}=await open('blocks');
  try{
   const start=await snap(p);
   await p.locator('[data-control="right"]').tap();const moved=await snap(p);
   await p.locator('[data-control="rotate"]').tap();const rotated=await snap(p);
   await p.locator('[data-control="drop"]').tap();const after=await snap(p);
   const locked=after.grid.flat().filter(Boolean).length;
   await shot(p,'block-after-hard-drop');
   if(moved.cur.x<=start.cur.x||locked<4||after.cur.idx===start.cur.idx&&after.cur.y>10||errors.length)throw Error(JSON.stringify({start:start.cur,moved:moved.cur,rotated:rotated.cur,after:after.cur,locked,errors}));
   return {movedX:start.cur.x+'->'+moved.cur.x,lockedCells:locked,currentPiece:after.cur.idx,lines:after.lines};
  }finally{await ctx.close()}
 });
 await test('mines-real-safe-reveal-flag-and-loss',async()=>{
  const {ctx,p,errors}=await open('mines');
  try{
   const first=await snap(p);
   await p.locator('#mineboard [data-tile="40"]').tap();
   const safe=await snap(p);
   const covered=safe.open.findIndex((v,i)=>!v&&safe.cells[i]!==9);
   if(covered<0)throw Error('no covered non-mine for flagging');
   await p.locator('[data-control="flag"]').first().tap();
   await p.locator('[data-tile="'+covered+'"]').tap();
   const flagged=await snap(p);
   // toggle out of flag mode and deliberately touch a genuine mine tile.
   await p.locator('[data-control="reveal"]').first().tap();
   const index=flagged.cells.findIndex((v,i)=>v===-1&&!flagged.flags[i]&&!flagged.open[i]);
   if(index<0)throw Error('could not identify hidden mine after first safe reveal; ids '+new Set(flagged.cells));
   await p.locator('[data-tile="'+index+'"]').tap();
   const loss=await p.evaluate(()=>window.__nightArcadeTest.flags().finished);
   await shot(p,'mines-after-losing');
   await p.locator('#restart').tap();const again=await snap(p);
   if(first.started||!safe.started||!flagged.flags[covered]||!loss||again.started||errors.length)throw Error(JSON.stringify({first:first.started,safe:safe.started,flagged:flagged.flags[covered],loss,restart:again.started,errors}));
   return {safeOpened:safe.open.filter(Boolean).length,flagTile:covered,mineTile:index,lost:loss,reset:!again.started};
  }finally{await ctx.close()}
 });
 await test('maze-real-steering-eat-dots-pause',async()=>{
  const {ctx,p,errors}=await open('maze');
  try{
   const start=await snap(p);
   await p.locator('[data-control="down"]').tap();await p.waitForTimeout(550);
   const moving=await snap(p);
   await p.locator('[data-control="pause"]').tap();
   const paused=await p.evaluate(()=>window.__nightArcadeTest.flags().paused);
   const score=await p.evaluate(()=>window.__nightArcadeTest.flags().points);
   await shot(p,'maze-steered');
   if(!paused||moving.p.x===start.p.x&&moving.p.y===start.p.y||score<0||errors.length)throw Error(JSON.stringify({start:start.p,moving:moving.p,paused,score,errors}));
   return {start:start.p,after:moving.p,score,paused,remainingDots:moving.total};
  }finally{await ctx.close()}
 });
}finally{await b.close();writeFileSync(O+'/arcade-play-cases.json',JSON.stringify(cases,null,2));console.log('SUMMARY',JSON.stringify({total:cases.length,passed:cases.filter(x=>x.pass).length,failures:cases.filter(x=>!x.pass)}).slice(0,5000));if(cases.some(x=>!x.pass))process.exitCode=1;}

