// Hands-on side-by-side browser audit: PlayJigsaw.net PRODUCTION vs NIGHT Play.
import {chromium} from 'playwright';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..','..');
const out=path.join(root,'jigsaw/research/audit-output');mkdirSync(out,{recursive:true});
const report={date:new Date().toISOString(),reference:'https://playjigsaw.net/puzzles/puz-orchard-goat-sheltered-edge-7894e43b',scenarios:[]};
const pause=ms=>new Promise(ok=>setTimeout(ok,ms));
const server=createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 let p=pathname==='/games'?'index.html':pathname.replace(/^\//,'');
 if(p==='jigsaw/'||p==='jigsaw')p='jigsaw/index.html';
 if(!p||p.includes('..')){res.writeHead(404);res.end();return;}
 const types={'.js':'text/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml'};
 try{const bytes=readFileSync(path.join(root,p));res.writeHead(200,{'content-type':types[path.extname(p)]||'text/plain'});res.end(bytes);}
 catch{res.writeHead(404);res.end('Missing '+p);}
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
const local='http://127.0.0.1:'+server.address().port+'/jigsaw/';
let browser;
const clean=e=>String(e?.message||e).slice(0,450);
async function capture(page,name,entry){
 const buff=await page.screenshot({type:'jpeg',quality:61,animations:'disabled',timeout:22000});
 writeFileSync(path.join(out,name+'.jpg'),buff);
 entry.screenshots??=[];entry.screenshots.push(name+'.jpg');
 if(buff.length<140000)console.log('COMPARISON_IMAGE_'+name.replaceAll('-','_').toUpperCase()+'='+buff.toString('base64'));
}
async function metrics(page){
 return page.evaluate(()=>{
  const box=el=>{if(!el)return null;const r=el.getBoundingClientRect();return {x:+r.x.toFixed(1),y:+r.y.toFixed(1),w:+r.width.toFixed(1),h:+r.height.toFixed(1),inside:r.top>=0&&r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth}};
  const controls=[...document.querySelectorAll('button,[role=button]')].filter(el=>{
    const b=el.getBoundingClientRect();return b.width>10&&b.height>10&&getComputedStyle(el).visibility!=='hidden';
  }).map(el=>({id:el.id||undefined,name:(el.innerText||'').trim().slice(0,35),title:el.getAttribute('title')||undefined,aria:el.getAttribute('aria-label')||undefined,b:box(el)}));
  const canvases=[...document.querySelectorAll('canvas')].map(el=>({id:el.id||'',native:[el.width,el.height],box:box(el)}));
  return {url:location.href,viewport:[innerWidth,innerHeight],scroll:[scrollX,scrollY],pageHeight:document.documentElement.scrollHeight,
   controls,canvases,playHUD:box(document.querySelector('.play-hud')),playMat:box(document.querySelector('.play-mat')),
   fullscreen:!!document.fullscreenElement,canvasCoverage:canvases[0]?.box?Math.round(1000*canvases[0].box.w*canvases[0].box.h/(innerWidth*innerHeight))/10:null,
   bodyText:document.body.innerText.slice(0,950)};
 });
}
async function swipe(page,from,to,mobile){
 if(mobile){
  const cdp=await page.context().newCDPSession(page);
  const send=async(type,x,y)=>cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x,y,id:71,radiusX:5,radiusY:5,force:.9}]});
  await send('touchStart',from.x,from.y);
  for(let k=1;k<=28;k++){const t=k/28;await send('touchMove',from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t);await pause(14);}
  await send('touchEnd',to.x,to.y);await cdp.detach();
 }else{
  await page.mouse.move(from.x,from.y);await page.mouse.down();await page.mouse.move(to.x,to.y,{steps:25});await page.mouse.up();
 }
}
async function original(mobile){
 const label='original-'+(mobile?'mobile':'desktop');
 const context=await browser.newContext({viewport:mobile?{width:390,height:844}:{width:1280,height:840},isMobile:mobile,hasTouch:mobile,deviceScaleFactor:1});
 const page=await context.newPage(),e={name:label,site:'playjigsaw.net',interactionLog:[],errors:[]};report.scenarios.push(e);
 page.on('pageerror',x=>e.errors.push(clean(x)));
 try{
  await page.goto(report.reference,{waitUntil:'domcontentloaded',timeout:50000});
  await pause(1500);e.detail=await metrics(page);
  await capture(page,label+'-selection',e);
  // Ask for a 36-piece round if available. Confirm the resulting URL after clicking play.
  const easy=page.locator('button[aria-label*="36 pieces"],button:has-text("36 pcs")').first();
  if(await easy.count()&&await easy.isVisible()){await easy.click({timeout:7500});e.selection='36 requested'}
  const startCandidates=[
   page.getByRole('link',{name:/play with/i}).first(),
   page.locator('a:has-text("Play with")').first(),
   page.getByRole('button',{name:/play with/i}).first(),
   page.getByRole('button',{name:/^play now$|^start puzzle$|^play$/i}).first()
  ];
  let played=false;
  for(const start of startCandidates){
   if(await start.count()&&await start.isVisible()){
    e.startText=await start.innerText();await start.click({timeout:12000});
    played=true;break;
   }
  }
  if(!played)e.errors.push('No playable start action found');
  await pause(2200);e.initial=await metrics(page);await capture(page,label+'-play',e);
  // Drag from independently observed actual pieces in reference screenshots.
  const points=mobile?
   [[{x:64,y:124},{x:162,y:400}],[{x:293,y:120},{x:240,y:426}],[{x:85,y:703},{x:193,y:478}]]:
   [[{x:106,y:174},{x:344,y:288}],[{x:1161,y:187},{x:863,y:369}],[{x:114,y:684},{x:365,y:479}]];
  for(let i=0;i<points.length;i++){
   try{await swipe(page,...points[i],mobile);e.interactionLog.push({drag:i+1,from:points[i][0],to:points[i][1],result:'dispatched'});}catch(x){e.interactionLog.push({drag:i+1,error:clean(x)})}
   await pause(300);
  }
  await capture(page,label+'-after-drag',e);
  for(const [name,selector] of [
   ['edges','button[title="Show Edges & Corners Only"]'],
   ['reference','button[title^="Reference Background:"]'],
   ['hint','button[aria-label="Auto-place Neighbor Hint"]'],
   ['zoom','button[title="Zoom In"]']
  ]){
   const b=page.locator(selector).first();
   if(await b.count()&&await b.isVisible()){
    try{await b.click({timeout:4000});e.interactionLog.push({action:name,result:'clicked'});await pause(200);}
    catch(x){e.interactionLog.push({action:name,error:clean(x)})}
   }else e.interactionLog.push({action:name,result:'not visibly available'});
  }
  await capture(page,label+'-tools',e);
  const more=page.locator('button[title="Action Menu"]');
  if(mobile&&await more.count()&&await more.isVisible()){
   await more.click();await pause(250);e.actionMenu=await metrics(page);await capture(page,label+'-mobile-menu',e);
  }
  e.after=await metrics(page);
 }catch(x){e.errors.push(clean(x));try{await capture(page,label+'-error',e)}catch{}}
 await context.close();
}
async function night(mobile){
 const label='night-'+(mobile?'mobile':'desktop');
 const context=await browser.newContext({viewport:mobile?{width:390,height:844}:{width:1280,height:840},isMobile:mobile,hasTouch:mobile,deviceScaleFactor:1});
 const page=await context.newPage(),e={name:label,site:'NIGHT Play current merged build',errors:[],interactionLog:[]};report.scenarios.push(e);
 page.on('pageerror',x=>e.errors.push(clean(x)));
 try{
  await page.goto(local,{waitUntil:'domcontentloaded',timeout:20000});
  await page.locator('#galleryGrid .gallery-card').first().waitFor({timeout:12000});
  await capture(page,label+'-selection',e);
  await page.locator('[data-theme="photo-alpine-lake"]').click();
  await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound,null,{timeout:25000});
  await pause(600);
  e.initial=await metrics(page);e.stateBefore=await page.evaluate(()=>{const s=window.__nightJigsawTest.snapshot();return {placed:s.placed,moves:s.moves,pieces:s.pieces.length,zoom:s.zoom,groupCount:s.groups,board:s.board}});
  await capture(page,label+'-play',e);
  for(let i=0;i<3;i++){
   const gesture=await page.evaluate(()=>{
    const s=window.__nightJigsawTest.snapshot(),c=document.querySelector('#puzzleCanvas'),r=c.getBoundingClientRect(),sx=r.width/c.width,sy=r.height/c.height;
    const possibles=s.pieces.filter(p=>!p.placed).map((p,idx)=>({...p,idx,
     py:r.top+(p.y+s.board.pieceHeight*.5)*sy,
     px:r.left+(p.x+s.board.pieceWidth*.5)*sx}));
    const visible=possibles.filter(p=>p.py>r.top+10&&p.py<innerHeight-45&&p.px>12&&p.px<innerWidth-12);
    const candidate=visible[visible.length-1];
    if(!candidate)return null;
    return {id:candidate.id,from:{x:candidate.px,y:candidate.py},to:{x:r.left+(candidate.correctX+s.board.pieceWidth*.5)*sx,y:r.top+(candidate.correctY+s.board.pieceHeight*.5)*sy}};
   });
   if(!gesture){e.interactionLog.push({drag:i+1,error:'no reachable piece'});break}
   try{await swipe(page,gesture.from,gesture.to,mobile);await pause(230);e.interactionLog.push({drag:i+1,id:gesture.id,result:'dispatched',state:await page.evaluate(()=>{const s=window.__nightJigsawTest.snapshot();return {placed:s.placed,moves:s.moves}})})}
   catch(x){e.interactionLog.push({drag:i+1,error:clean(x)})}
  }
  e.stateAfterDrags=await page.evaluate(()=>{const s=window.__nightJigsawTest.snapshot();return {placed:s.placed,moves:s.moves,groups:s.groups}});
  await capture(page,label+'-after-drag',e);
  for(const [name,id] of [['edges','#edgesButton'],['reference','#previewButton'],['hint','#hintButton'],['zoom','#zoomIn']]){
   try{const button=page.locator(id);await button.click({timeout:5000});e.interactionLog.push({action:name,result:'clicked'});if(name==='reference'){await pause(300);await capture(page,label+'-preview',e);await page.locator('[data-close="previewDialog"]').click();}else await pause(220)}
   catch(x){e.interactionLog.push({action:name,error:clean(x)})}
  }
  await capture(page,label+'-tools',e);
  const full=page.locator('#fullscreenButton');
  if(await full.count()){
   try{await full.click({timeout:5000});await pause(250);e.fullscreen=await metrics(page);await capture(page,label+'-fullscreen',e);}
   catch(x){e.interactionLog.push({action:'fullscreen',error:clean(x)})}
  }
  e.after=await metrics(page);
 }catch(x){e.errors.push(clean(x));try{await capture(page,label+'-error',e)}catch{}}
 await context.close();
}
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 await original(false);await night(false);await original(true);await night(true);
}finally{await browser?.close();await new Promise(ok=>server.close(ok));}
writeFileSync(path.join(out,'comparison.json'),JSON.stringify(report,null,2));
for(const s of report.scenarios){
 const compact={name:s.name,errors:s.errors,selected:s.selection,game:s.initial?.url,start:s.startText,viewport:s.initial?.viewport,canvasCoverage:s.initial?.canvasCoverage,
    pageHeight:s.initial?.pageHeight,canvas:s.initial?.canvases?.[0],hud:s.initial?.playHUD,controlCount:s.initial?.controls?.length,
    controls:s.initial?.controls?.map(c=>({label:c.aria||c.title||c.name,location:c.b,inside:c.b?.inside})).slice(0,23),
    moves:s.interactionLog,after:s.stateAfterDrags,fullscreen:s.fullscreen?.fullscreen,shots:s.screenshots};
 console.log('COMPARISON_SUMMARY='+JSON.stringify(compact));
}
console.log('COMPARISON_DONE');
