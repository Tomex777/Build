/**
 * Public, no-login browser interaction study of PlayJigsaw production and its
 * separately published MIT demonstration. Does not submit scores or modify data.
 */
import {chromium} from 'playwright';
import {mkdirSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const OUT='night-play/jigsaw/research/screenshots';mkdirSync(OUT,{recursive:true});
const report={at:new Date().toISOString(),scenarios:[]};
const delay=ms=>new Promise(r=>setTimeout(r,ms));
function describe(e){return String(e?.message||e).slice(0,350)}
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
async function shot(page,id){
 try{
  const buffer=await page.screenshot({type:'jpeg',quality:49,animations:'disabled',timeout:20000});
  writeFileSync(OUT+'/'+id+'.jpg',buffer);
  const hash=createHash('sha256').update(buffer).digest('hex').slice(0,16);
  console.log('SHOT '+id+' sha256='+hash+' bytes='+buffer.length);
  if(buffer.length<210000)console.log('REFERENCE_SCREENSHOT_'+id.replaceAll('-','_').toUpperCase()+'='+buffer.toString('base64'));
  return hash;
 }catch(e){console.log('SCREENSHOT_ERROR '+id+' '+describe(e));return null;}
}
async function describePage(page){
 return page.evaluate(()=>{
  const visible=el=>{const b=el.getBoundingClientRect();return b.width>0&&b.height>0&&getComputedStyle(el).visibility!=='hidden'};
  return {
   url:location.href,title:document.title,
   headings:[...document.querySelectorAll('h1,h2,h3')].filter(visible).slice(0,12).map(el=>el.textContent.trim().slice(0,110)),
   buttons:[...document.querySelectorAll('button,[role=button]')].filter(visible).slice(0,65).map(el=>({text:el.innerText?.trim().slice(0,75)||'',aria:el.getAttribute('aria-label')||'',title:el.getAttribute('title')||''})),
   canvases:[...document.querySelectorAll('canvas')].filter(visible).map(el=>({id:el.id,width:el.width,height:el.height,css:{w:Math.round(el.getBoundingClientRect().width),h:Math.round(el.getBoundingClientRect().height)}})),
   linkSample:[...document.querySelectorAll('a')].filter(visible).slice(0,20).map(el=>({text:el.textContent.trim().slice(0,35),href:el.getAttribute('href')})),
   scripts:[...document.querySelectorAll('script[src]')].slice(0,12).map(el=>el.src),
   bodyText:document.body.innerText.slice(0,1800)
  }
 });
}
async function dragMouse(page,canvas){
 const b=await canvas.boundingBox();if(!b)return {error:'missing canvas box'};
 const from={x:b.x+b.width*.83,y:b.y+b.height*.54},to={x:b.x+b.width*.49,y:b.y+b.height*.48};
 await page.mouse.move(from.x,from.y);await page.mouse.down();
 await page.mouse.move(to.x,to.y,{steps:22});await page.mouse.up();
 return {from,to};
}
async function dragTouch(page,canvas){
 const b=await canvas.boundingBox();if(!b)return {error:'missing canvas box'};
 const cdp=await page.context().newCDPSession(page);
 const from={x:Math.round(b.x+b.width*.80),y:Math.round(b.y+b.height*.57)};
 const to={x:Math.round(b.x+b.width*.44),y:Math.round(b.y+b.height*.48)};
 const dispatch=(type,x,y)=>cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x,y,id:1,radiusX:5,radiusY:5,force:0.8}]});
 await dispatch('touchStart',from.x,from.y);
 for(let i=1;i<=18;i++){const t=i/18;await dispatch('touchMove',Math.round(from.x+(to.x-from.x)*t),Math.round(from.y+(to.y-from.y)*t));await delay(15)}
 await dispatch('touchEnd',to.x,to.y);await cdp.detach();return {from,to};
}
async function openProduction(name,mobile=false){
 const context=await browser.newContext({viewport:mobile?{width:390,height:844}:{width:1280,height:840},isMobile:mobile,hasTouch:mobile,deviceScaleFactor:1,locale:'en-US'});
 const page=await context.newPage();const entry={name,site:'playjigsaw.net',mobile,errors:[],requests:[]};report.scenarios.push(entry);
 page.on('pageerror',e=>entry.errors.push('JS '+describe(e)));
 page.on('requestfailed',r=>{if(entry.requests.length<15)entry.requests.push({url:r.url().slice(0,180),error:r.failure()?.errorText})});
 try{
  await page.goto('https://playjigsaw.net/puzzles/puz-orchard-goat-sheltered-edge-7894e43b',{waitUntil:'domcontentloaded',timeout:55000});
  await delay(1800);entry.detail=await describePage(page);await shot(page,name+'-detail');
  // Try the 36-piece puzzle for enough space to see individual loose pieces.
  let selector=page.getByRole('button',{name:/36\s*(?:pieces|pcs)?/i}).first();
  if(await selector.count()&&await selector.isVisible()) {await selector.click({timeout:5000});entry.pieceChoice='36 selected'}else entry.pieceChoice='36 button not found';
  const playButtons=[page.getByRole('button',{name:/play with/i}).first(),page.getByRole('button',{name:/^play now$|^play puzzle$|^start puzzle$|^play$/i}).first(),page.getByRole('link',{name:/play with/i}).first()];
  let started=false;
  for(const target of playButtons){if(await target.count()&&await target.isVisible()){entry.playButton=await target.first().innerText().catch(()=>'?');await target.click({timeout:7500});started=true;break;}}
  entry.playButtonFound=started;await delay(2300);
  entry.game=await describePage(page);console.log('PRODUCTION_GAME '+name+' '+JSON.stringify({url:entry.game.url,headings:entry.game.headings,buttons:entry.game.buttons,canvases:entry.game.canvases,body:entry.game.bodyText.slice(0,900)}));
  const before=await shot(page,name+'-before');entry.screenshotBefore=before;
  const candidates=page.locator('canvas:visible');const count=await candidates.count();entry.canvasCount=count;
  if(count){
   const canvas=candidates.first();
   entry.drag=mobile?await dragTouch(page,canvas):await dragMouse(page,canvas);
   await delay(450);entry.screenshotAfter=await shot(page,name+'-after-drag');
   if(!mobile){const b=await canvas.boundingBox();if(b){await page.mouse.move(b.x+b.width*.5,b.y+b.height*.5);await page.mouse.wheel(0,-450);await delay(550);entry.screenshotZoom=await shot(page,name+'-after-wheel');}}
   for(const rx of [/edge pieces/i,/^edges?$/i,/^edge filter$/i]){
    let b=page.getByRole('button',{name:rx}).first();if(await b.count()&&await b.isVisible()){await b.click({timeout:3000});entry.edgeTool=rx.source;await delay(450);entry.screenshotEdge=await shot(page,name+'-edge-filter');break}
   }
  }
  entry.after=await describePage(page);
  if(!started)entry.errors.push('Could not identify Play button from rendered detail');
 }catch(e){entry.errors.push(describe(e));console.log('PRODUCTION_ERROR '+name+' '+describe(e));try{await shot(page,name+'-error')}catch{}}
 await context.close();
}
async function openGithubDemo(){
 const context=await browser.newContext({viewport:{width:1280,height:840},deviceScaleFactor:1});
 const page=await context.newPage(),entry={name:'github-demo',site:'public MIT GitHub engine',errors:[]};report.scenarios.push(entry);
 page.on('pageerror',e=>entry.errors.push(describe(e)));
 try{
  await page.goto('http://127.0.0.1:8765/',{waitUntil:'domcontentloaded',timeout:12000});await delay(1300);
  entry.start=await describePage(page);entry.screenshotBefore=await shot(page,'github-demo-before');
  const canvas=page.locator('#puzzle-canvas');
  if(await canvas.count()){entry.drag=await dragMouse(page,canvas);await delay(350);entry.screenshotAfter=await shot(page,'github-demo-after-drag');}
  entry.after=await describePage(page);
 }catch(e){entry.errors.push(describe(e));console.log('GITHUB_DEMO_ERROR '+describe(e));await shot(page,'github-demo-error');}
 await context.close();
}
try{await openProduction('production-desktop',false);await openProduction('production-mobile',true);await openGithubDemo()}finally{await browser.close()}
writeFileSync(OUT+'/reference-report.json',JSON.stringify(report,null,2));
console.log('REFERENCE_AUDIT_JSON='+JSON.stringify(report));
console.log('REFERENCE_AUDIT_DONE');
