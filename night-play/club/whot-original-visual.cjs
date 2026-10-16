'use strict';
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),http=require('node:http'),path=require('node:path');
const root=process.env.REF_BUILD||'/tmp/akpeti-visual/build';
const server=http.createServer((req,res)=>{
 let name=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
 if(!path.extname(name))name='/index.html';
 const full=path.resolve(root,'.'+name);
 if(!full.startsWith(root+'/')){res.writeHead(403);return res.end()}
 try{const ext=path.extname(full);res.writeHead(200,{'content-type':ext==='.js'?'application/javascript':ext==='.css'?'text/css':ext==='.jpg'||ext==='.jpeg'?'image/jpeg':ext==='.png'?'image/png':'text/html'});res.end(fs.readFileSync(full))}
 catch{res.writeHead(404);res.end()}
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{for(const width of [390,1280]){
  const page=await browser.newPage({viewport:{width,height:844},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:'+server.address().port+'/play-computer');
  await page.waitForTimeout(900);
  const metrics=await page.evaluate(()=>{
   const el=x=>{const e=document.querySelector(x);if(!e)return null;const r=e.getBoundingClientRect(),c=getComputedStyle(e);return {x:r.x,y:r.y,width:r.width,height:r.height,gap:c.gap,font:c.fontSize,display:c.display}};
   return {app:el('.App'),rows:[...document.querySelectorAll('.scroll-container')].map(e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,cards:e.querySelectorAll('[class*="card"]').length}}),
   center:el('[class*="center_area"]'),centerCards:[...document.querySelectorAll('[class*="center_area"]>[class*="card"]')].map(e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}}),
   firstCard:el('[class*="card__"]'),title:document.title,error:document.body.innerText.slice(0,130)};
  });
  console.log('AKPETI_VISUAL_METRICS_'+width+'='+JSON.stringify(metrics));console.log('AKPETI_VISUAL_ERRORS_'+width+'='+errors.join('|'));
  const shot=(await page.screenshot({type:'jpeg',quality:48})).toString('base64');
  console.log('AKPETI_VISUAL_JPEG_'+width+'='+shot);
  await page.close();
 }}finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});
