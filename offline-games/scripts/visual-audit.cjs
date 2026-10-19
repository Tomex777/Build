#!/usr/bin/env node
/* NIGHT Offline Games — REAL Chromium interaction + screenshot audit.
   Does not claim these quick scripted inputs equal human playtesting.
   Each classic/arcade/puzzle is loaded, interacted with, and photographed.
   Android-native 2048 is explicitly recorded as requiring device test.
*/
'use strict';
const fs = require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../app/src/main/assets');
const out=path.resolve(__dirname,'../qa-evidence');
const gameList=fs.readFileSync(path.resolve(__dirname,'../app/src/main/java/com/night/offlinegames/MainActivity.kt'),'utf8');
const entries=[...gameList.matchAll(/GameEntry\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)"/g)].map(m=>({id:m[1],name:m[2],category:m[3]}));
const allow=new Set(['mines','blocks','bird','maze']);
const puzzle=new Set(['sudoku','connect']);
const mime={'.html':'text/html','.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.webp':'image/webp','.json':'application/json'};
const serve=http.createServer((req,res)=>{
 let filename;
 try {filename=path.resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));}catch{res.writeHead(400);res.end();return}
 if(filename!==root&&!filename.startsWith(root+path.sep)){res.writeHead(403);res.end();return}
 fs.readFile(filename,(error,data)=>{if(error){res.writeHead(404);res.end('NOT FOUND '+req.url);return}res.writeHead(200,{'content-type':mime[path.extname(filename)]||'application/octet-stream','cache-control':'no-store'});res.end(data)});
});
const safe=s=>s.replace(/[^a-z0-9_-]/gi,'_');
const delay=t=>new Promise(resolve=>setTimeout(resolve,t));
async function audit(browser,serverPort,entry,worker){
 const id=entry.id;
 if(id==='2048')return{id,name:entry.name,category:entry.category,level:'native-app-pending',notes:['Native Compose game cannot be played in a browser; must test in emulator/device.']};
 const dir=allow.has(id)?'arcade':puzzle.has(id)?'puzzles':'classics';
 const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1,isMobile:true,hasTouch:true});
 const errors=[],failedRequests=[];let interacted=false,action='',statusBefore='',statusAfter='',firstSnap='',lastSnap='',measures={};
 page.on('pageerror',error=>errors.push(error.message.slice(0,300)));
 page.on('console',m=>{if(m.type()==='error')errors.push('Console: '+m.text().slice(0,250))});
 page.on('response',resp=>{if(resp.status()>=400)failedRequests.push(resp.status()+' '+new URL(resp.url()).pathname)});
 await page.route('**/*',route=>{if(!route.request().url().startsWith('http://127.0.0.1:'+serverPort)){failedRequests.push('Network '+route.request().url());return route.abort()}return route.continue()});
 try{
   const response=await page.goto('http://127.0.0.1:'+serverPort+'/'+dir+'/index.html?game='+encodeURIComponent(id),{waitUntil:'load',timeout:19000});
   if(response.status()!==200)errors.push('document HTTP '+response.status());
   await page.waitForTimeout(200);
   const board=dir==='classics'?'#board':dir==='puzzles'?'#board':'.canvas-shell';
   measures=await page.evaluate(board=>{
     const b=document.querySelector(board),area=b?.getBoundingClientRect(),body=document.body;
     return{title:(document.querySelector('#title')?.textContent||'').trim().slice(0,70),boardWidth:area?.width||0,boardHeight:area?.height||0,boardContent:b?.children.length||0,buttons:b?.querySelectorAll('button').length||0,canvas:b?.querySelectorAll('canvas').length||0,overflowX:document.documentElement.scrollWidth>window.innerWidth+3,pageWidth:document.documentElement.scrollWidth,visibleWidth:window.innerWidth,imagesMissing:[...document.images].filter(i=>i.complete&&i.naturalWidth===0).length,fontSize:parseFloat(getComputedStyle(document.querySelector('#status')||document.querySelector('#statusbar')||body).fontSize||'0')};
   },board);
   if(measures.boardWidth<120||measures.boardHeight<80)errors.push('Missing/tiny board: '+measures.boardWidth+'x'+measures.boardHeight);
   if(measures.overflowX)errors.push('Horizontal overflow '+measures.pageWidth+' > '+measures.visibleWidth);
   const snapshot=page.locator(dir==='classics'?'#status':dir==='puzzles'?'#message':'#score');
   statusBefore=(await snapshot.count())?(await snapshot.first().innerText().catch(()=>'')):'';
   const folder=path.join(out,'screenshots');fs.mkdirSync(folder,{recursive:true});
   firstSnap='screenshots/'+safe(id)+'-initial.png';await page.screenshot({path:path.join(out,firstSnap),fullPage:false,timeout:9000});
   if(dir==='classics'){
     const b=page.locator('#board');
     const candidates=['#actions button:not([disabled])','#board button:not([disabled])','#board canvas'];
     const first=candidates.map(s=>page.locator(s).first());
     let target=null;for(const c of first)if(await c.count()&&await c.isVisible()){target=c;break}
     // Speed is a simultaneous CPU race: the human may have no legal card until
     // the computer changes a centre pile. Wait instead of counting it broken.
     if(!target&&id==='speed'){
       for(let t=0;t<8&&!target;t++){
         await page.waitForTimeout(600);
         for(const candidate of first)if(await candidate.count()&&await candidate.isVisible()){target=candidate;break}
       }
     }
     if(target){
       if(await target.evaluate(x=>x.tagName==='CANVAS')){
         const bb=await target.boundingBox();
         if(bb){await page.mouse.move(bb.x+bb.width*.25,bb.y+bb.height*.68);await page.mouse.down();await page.mouse.move(bb.x+bb.width*.63,bb.y+bb.height*.42,{steps:6});await page.mouse.up();interacted=true;action='canvas drag+tap';}
       }else{await target.click({timeout:3000,force:true});interacted=true;action='button click';}
     }
     // Poke board following any Start action to cover real board interaction.
     if(await b.locator('button:not([disabled])').count()){
       const cell=b.locator('button:not([disabled])').first();
       if(await cell.isVisible().catch(()=>false)){await cell.click({force:true,timeout:2500}).catch(()=>{});interacted=true;action+=' + board tap';}
     }
   }else if(dir==='arcade'){
     const board=page.locator('#screen');
     const bb=await board.boundingBox();
     if(bb){await page.mouse.move(bb.x+bb.width*.5,bb.y+bb.height*.5);await page.mouse.down();await page.mouse.move(bb.x+bb.width*.67,bb.y+bb.height*.39,{steps:4});await page.mouse.up();interacted=true;action='arcade canvas gesture'}
     const mineBtn=page.locator('#mineboard button:not([disabled])').first();
     if(await mineBtn.count()&&await mineBtn.isVisible()){await mineBtn.click({force:true,timeout:2000}).catch(()=>{});interacted=true;action='mines board tap'}
   }else{
     const btn=page.locator('#board button:not([disabled])').first();
     if(await btn.count()&&await btn.isVisible()){await btn.click({force:true,timeout:2000});interacted=true;action='puzzle board tap'}
   }
   await page.waitForTimeout(520);
   statusAfter=(await snapshot.count())?(await snapshot.first().innerText().catch(()=>'')):'';
   lastSnap='screenshots/'+safe(id)+'-played.png';await page.screenshot({path:path.join(out,lastSnap),fullPage:false,timeout:9000});
   if(!interacted)errors.push('No playable input found');
 }catch(e){errors.push('AUDIT '+String(e).slice(0,350))}
 finally{await page.close().catch(()=>{})}
 return{id,name:entry.name,category:entry.category,dir,action,interacted,statusBefore,statusAfter,measures,initial:firstSnap,played:lastSnap,errors:[...new Set(errors)],failedRequests:[...new Set(failedRequests)].slice(0,8)};
}
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const started=Date.now();
 await new Promise(resolve=>serve.listen(0,'127.0.0.1',resolve));
 const port=serve.address().port;
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu']});
 let index=0;const results=[];
 async function work(n){while(index<entries.length){const entry=entries[index++];const r=await audit(browser,port,entry,n);results.push(r);console.log('GAME_AUDIT '+JSON.stringify({id:r.id,interacted:r.interacted,errorCount:(r.errors||[]).length,errors:(r.errors||[]).slice(0,2),requests:r.failedRequests?.length||0}));}}
 try{await Promise.all(Array.from({length:4},(_,i)=>work(i)))}
 finally{await browser.close();serve.close()}
 results.sort((a,b)=>entries.findIndex(e=>e.id===a.id)-entries.findIndex(e=>e.id===b.id));
 const totals={registered:entries.length,browserPlayed:results.filter(x=>x.interacted).length,passing:results.filter(x=>x.interacted&&x.errors.length===0&&!x.failedRequests.length).length,errors:results.filter(x=>x.errors?.length||x.failedRequests?.length).length,nativePending:results.filter(x=>x.level==='native-app-pending').length,durationSeconds:Math.round((Date.now()-started)/1000)};
 fs.writeFileSync(path.join(out,'audit.json'),JSON.stringify({totals,results},null,2));
 fs.writeFileSync(path.join(out,'summary.md'),'# NIGHT Offline Games · browser interaction audit\n\n'+Object.entries(totals).map(([k,v])=>'- '+k+': '+v).join('\n')+'\n\n**This is automated input+visual capture, not human play-to-completion. Android emulator still required.**\n');
 console.log('QA_TOTALS '+JSON.stringify(totals));
 if(results.length!==entries.length)process.exitCode=3;
 if(totals.browserPlayed<entries.length-1)process.exitCode=2;
})().catch(err=>{console.error(err);process.exitCode=3;serve.close()});
