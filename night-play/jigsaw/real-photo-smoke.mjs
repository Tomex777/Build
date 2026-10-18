import {chromium} from 'playwright';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const out=path.join(root,'visual-output');mkdirSync(out,{recursive:true});
const server=createServer((req,res)=>{
 let f=new URL(req.url,'http://localhost').pathname.replace(/^\//,'');
 if(f==='jigsaw'||f==='jigsaw/')f='jigsaw/index.html';
 if(!f.startsWith('jigsaw/')||f.includes('..')){res.writeHead(404);res.end();return}
 try{const bytes=readFileSync(path.join(root,f));res.writeHead(200,{'content-type':f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html'});res.end(bytes)}
 catch{res.writeHead(404);res.end();}
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));page.on('console',m=>{if(m.type()==='error'&&errors.length<20)errors.push('console '+m.text().slice(0,220))});
 await page.goto('http://127.0.0.1:'+server.address().port+'/jigsaw/',{waitUntil:'domcontentloaded'});
 await page.locator('#galleryGrid .gallery-image img').first().waitFor({timeout:10000});
 await page.waitForFunction(()=>[...document.querySelectorAll('.gallery-image img')].some(img=>img.complete&&img.naturalWidth>=300),null,{timeout:28000}).catch(()=>{});
 const photos=await page.locator('.gallery-image img').evaluateAll(imgs=>imgs.map(img=>({alt:img.alt,width:img.naturalWidth,loaded:img.complete,url:img.currentSrc.slice(0,120)})));
 console.log('REAL_PHOTO_QA_GALLERY='+JSON.stringify(photos));
 const ga=await page.screenshot({type:'jpeg',quality:65,fullPage:true});
 writeFileSync(path.join(out,'JIGSAW-LIVE-PHOTOS-GALLERY.jpg'),ga);
 console.log('LIVE_PHOTO_GALLERY_BASE64='+ga.toString('base64'));
 await page.locator('[data-theme="photo-alpine-lake"]').click();
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound,null,{timeout:28000}).catch(()=>{});
 const state=await page.evaluate(()=>({
   active:window.__nightJigsawTest?.snapshot().activeRound,
   placed:window.__nightJigsawTest?.snapshot().placed,
   board:window.__nightJigsawTest?.snapshot().board,
   imageVariance:(()=>{const canvas=document.getElementById('puzzleCanvas'),c=canvas.getContext('2d');const arr=c.getImageData(0,0,canvas.width,canvas.height).data;let min=255,max=0;for(let j=0;j<arr.length;j+=167){min=Math.min(min,arr[j]);max=Math.max(max,arr[j]);}return max-min;})()
 }));
 console.log('REAL_PHOTO_QA_GAME='+JSON.stringify({state,errors}));
 const gp=await page.screenshot({type:'jpeg',quality:65,fullPage:true});
 writeFileSync(path.join(out,'JIGSAW-LIVE-PHOTO-PLAY.jpg'),gp);
 console.log('LIVE_PHOTO_GAME_BASE64='+gp.toString('base64'));
 if(!state.active||photos[0].width<300||state.imageVariance<15)process.exitCode=1;
 }finally{await browser?.close();await new Promise(ok=>server.close(ok));}
