'use strict';
// This browser test runs the original app from a *temporary* checkout on CI.
// Do not copy, bundle or republish upstream source into NIGHT Play.
const fs=require('node:fs'),http=require('node:http'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const nightRoot=path.resolve(__dirname,'..');
const originalRoot=process.env.AKPETI_BUILD||'/tmp/night-whot-reference/build';
const mime={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png','.jpg':'image/jpeg','.svg':'image/svg+xml','.ico':'image/x-icon','.json':'application/json','.webp':'image/webp'};
function serve(root,night){
 return http.createServer((req,res)=>{
  let pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  if(night){
   if(pathname==='/club'||pathname==='/club/')pathname='/club/index.html';
   if(pathname==='/whot-lab'||pathname==='/whot-lab/')pathname='/whot-lab/index.html';
   if(pathname==='/games')pathname='/index.html';
  }else if(!path.extname(pathname))pathname='/index.html';
  const filepath=path.resolve(root,'.'+pathname);
  if(!filepath.startsWith(root+path.sep)){res.writeHead(403);res.end();return}
  try{
   const st=fs.statSync(filepath);
   if(!st.isFile())throw new Error('not file');
   res.writeHead(200,{'content-type':mime[path.extname(filepath)]||'application/octet-stream','cache-control':'no-store'});
   res.end(fs.readFileSync(filepath));
  }catch{
   if(!night&&path.extname(pathname)==='.html'){
    res.writeHead(200,{'content-type':'text/html'});res.end(fs.readFileSync(path.join(root,'index.html')));
   }else{res.writeHead(404);res.end('Not found: '+pathname)}
  }
 });
}
(async()=>{
 assert.ok(fs.existsSync(path.join(originalRoot,'index.html')),'the original AkpetiTrust React source was built before this test');
 const night=serve(nightRoot,true),original=serve(originalRoot,false);
 await Promise.all([new Promise(r=>night.listen(0,'127.0.0.1',r)),new Promise(r=>original.listen(0,'127.0.0.1',r))]);
 const n='http://127.0.0.1:'+night.address().port,o='http://127.0.0.1:'+original.address().port;
 let browser;
 try{
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  for(const width of [390,1280]){
   const context=await browser.newContext({viewport:{width,height:844},isMobile:width<800,hasTouch:width<800});
   const upstream=await context.newPage();
   const upstreamErrors=[];upstream.on('pageerror',e=>upstreamErrors.push(e.message));
   await upstream.goto(o+'/play-computer',{waitUntil:'domcontentloaded'});
   await upstream.locator('#root').waitFor({state:'visible',timeout:20000});
   await upstream.waitForTimeout(950);
   const upstreamMetrics=await upstream.evaluate(()=>{
    const cardRoot=document.querySelector('[class*="card"]');
    const card=cardRoot?.getBoundingClientRect();
    return {title:document.title,bodyLength:document.body.innerText.length,rootChildren:document.querySelector('#root')?.children.length||0,
      cards:document.querySelectorAll('[class*="card"]').length,
      firstCard:card?{width:Math.round(card.width),height:Math.round(card.height)}:null,
      path:location.pathname};
   });
   assert.ok(upstreamMetrics.rootChildren>=1&&upstreamMetrics.bodyLength>10,'original game must actually render text');
   assert.ok(upstreamMetrics.cards>0,'original source must actually render card components');
   assert.deepEqual(upstreamErrors,[],'upstream browser JS must run cleanly');
   console.log('AKPETI_ORIGINAL_BUILT_PLAYABLE',width,JSON.stringify(upstreamMetrics));
   const our=await context.newPage(),errors=[];
   our.on('pageerror',e=>errors.push(e.message));
   await our.goto(n+'/club/?game=whot');
   await our.locator('.whot-scoreboard').waitFor({state:'visible'});
   const own=await our.evaluate(()=>({cards:document.querySelectorAll('.hand .whot-card').length,
    board:!!document.querySelector('.table-surface'),motion:window.__nightWhotMotion()?.timings||null}));
   assert.equal(own.cards,5,'NIGHT WHOT still deals the expected five cards');
   assert.deepEqual(errors,[],'NIGHT game remains valid');
   console.log('NIGHT_WHOT_PLAYABLE',width,JSON.stringify(own));
   const lab=await context.newPage();
   const labErrors=[];lab.on('pageerror',e=>labErrors.push(e.message));
   await lab.goto(n+'/whot-lab/');
   assert.match(await lab.title(),/WHOT Motion Lab/);
   assert.equal(await lab.locator('iframe').count(),2,'comparison contains original and NIGHT game');
   assert.equal(await lab.locator('#nightFrame').getAttribute('src'),'/club/?game=whot');
   assert.equal(await lab.locator('#originalFrame').getAttribute('src'),'https://whot.trust-akpeti.com/play-computer');
   assert.equal(await lab.getByRole('link',{name:/Open the original game/}).count(),1);
   if(width<800){
    assert.equal(await lab.locator('[data-panel="night"]').isVisible(),true);
    await lab.getByRole('button',{name:'AkpetiTrust original'}).click();
    assert.equal(await lab.locator('[data-panel="original"]').isVisible(),true);
    assert.equal(await lab.locator('[data-panel="night"]').isVisible(),false);
    await lab.getByRole('button',{name:'NIGHT Play',exact:true}).click();
    assert.equal(await lab.locator('[data-panel="night"]').isVisible(),true);
   }else{
    assert.equal(await lab.locator('[data-panel="night"]').isVisible(),true);
    assert.equal(await lab.locator('[data-panel="original"]').isVisible(),true);
   }
   assert.deepEqual(labErrors,[],'comparison lab has no script errors');
   console.log('NIGHT_WHOT_LAB_PASS',width);
   await context.close();
  }
  console.log('PASS: Original AkpetiTrust/whot cloned, built, launched in Chromium and compared beside NIGHT WHOT. No third-party source distributed.');
 }finally{
  if(browser)await browser.close();
  await Promise.all([new Promise(r=>night.close(r)),new Promise(r=>original.close(r))]);
 }
})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
