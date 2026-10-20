import assert from "node:assert/strict";
import {createServer} from "node:http";
import {readFileSync, mkdirSync} from "node:fs";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {chromium} from "playwright";

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),"..");
const mime={".html":"text/html",".js":"text/javascript",".css":"text/css",".svg":"image/svg+xml",".png":"image/png",".jpg":"image/jpeg"};
const server=createServer((req,res)=>{
  const pathname=new URL(req.url,"http://localhost").pathname;
  const rel=pathname==="/club"||pathname==="/club/"?"club/index.html":pathname.slice(1);
  const file=path.resolve(root,rel);
  if(!file.startsWith(root+path.sep)){res.writeHead(403).end();return;}
  try{const body=readFileSync(file);res.writeHead(200,{"content-type":(mime[path.extname(file)]||"application/octet-stream"),"cache-control":"no-store"});res.end(body);}
  catch{res.writeHead(404).end("Not found");}
});
await new Promise(ok=>server.listen(0,"127.0.0.1",ok));
let browser;
const base="http://127.0.0.1:"+server.address().port;
try{
 browser=await chromium.launch({headless:true,channel:"chrome",args:["--no-sandbox"]});
 mkdirSync("night-play/visual-output",{recursive:true});
 for(const width of [320,390]){
  const page=await browser.newPage({viewport:{width,height:width===320?700:844},isMobile:true,hasTouch:true});
  const errors=[];page.on("pageerror",e=>errors.push(e.message));
  await page.goto(base+"/club/?game=checkers");
  await page.evaluate(()=>{
   const saved=JSON.parse(localStorage.getItem('night-play-club-v1'));
   const cells=Array(64).fill(null);cells[40]='w';cells[44]='w';cells[33]='b';cells[19]='b';
   saved.checkers={cells,turn:'w',selected:null,forced:null,winner:null,mode:'local',msg:'Final capture chain test.'};
   localStorage.setItem('night-play-club-v1',JSON.stringify(saved));
  });
  await page.reload();
  await page.locator('[data-checker="40"]').tap();
  await page.locator('[data-checker="26"]').tap();
  await page.waitForFunction(()=>window.__nightCheckersQA().pieces[26]==='w'&&!window.__nightCheckersQA().busy);
  await page.locator('[data-checker="12"]').tap();
  await page.waitForFunction(()=>window.__nightCheckersQA().winner==='w'&&!window.__nightCheckersQA().busy);
  const check=page.locator('.checker-frame .checker-result-layer');
  assert.equal(await check.count(),1,"winner is inside draughts frame");
  assert.match(await check.innerText(),/White wins!/);
  assert.equal(await page.locator('#newRound').isVisible(),false,"no duplicate new-game button at finish");
  const geom=await page.evaluate(()=>{
   const board=document.querySelector('.checker-frame').getBoundingClientRect();
   const result=document.querySelector('.checker-result-card, .checker-result-layer .club-result-card').getBoundingClientRect();
   return {contained:result.left>=board.left-1&&result.right<=board.right+1&&result.top>=board.top-1&&result.bottom<=board.bottom+1,overflow:document.documentElement.scrollWidth>innerWidth+2};
  });
  assert.equal(geom.contained,true,"result visually contained by board "+width);
  assert.equal(geom.overflow,false,"no horizontal overflow "+width);
  await page.screenshot({path:'night-play/visual-output/A0-CHECKERS-INBOARD-'+width+'.png'});
  await check.locator('[data-action="checkers-reset"]').tap();
  assert.equal(await page.locator('.checker-result-layer').count(),0,"checkers restarts cleanly");
  assert.equal(await page.locator('#newRound').isVisible(),true,"standard restart comes back after reset");
  assert.equal(await page.locator('.checker-piece').count(),24,"pieces reset");
  await page.goto(base+"/club/?game=snakes");
  await page.evaluate(()=>{
   const saved=JSON.parse(localStorage.getItem('night-play-club-v1'));
   saved.snakes.players.forEach(p=>p.pos=99);
   saved.snakes.turn=0;saved.snakes.winner=null;saved.snakes.last=0;
   localStorage.setItem('night-play-club-v1',JSON.stringify(saved));
  });
  await page.reload();
  let tries=0;
  while(await page.locator('.snakes-result-layer').count()===0 && tries++<70){
    await page.locator('[data-action="snakes-roll"]').tap();
  }
  assert.ok(tries<70,"a real roll produced exact-square-100 victory");
  const snk=page.locator('.snakes-board .snakes-result-layer');
  assert.match(await snk.innerText(),/wins!/);
  assert.equal(await page.locator('#newRound').isVisible(),false,"snakes does not show duplicate restart");
  const contained=await page.evaluate(()=>{
   const b=document.querySelector('.snakes-board').getBoundingClientRect();
   const c=document.querySelector('.snakes-result-layer .club-result-card').getBoundingClientRect();
   return c.top>=b.top-1&&c.bottom<=b.bottom+1&&c.left>=b.left-1&&c.right<=b.right+1;
  });
  assert.equal(contained,true,"snakes result is inside playing field "+width);
  await page.screenshot({path:'night-play/visual-output/A0-SNAKES-INBOARD-'+width+'.png'});
  await snk.locator('[data-action="snakes-reset"]').tap();
  assert.equal(await page.locator('.snakes-result-layer').count(),0,"snakes retry removes result");
  assert.match(await page.locator('#status').innerText(),/Red.s turn/);
  assert.deepEqual(errors,[],"no JS page errors");
  console.log('PASS: '+width+'px actual touch checkers double-capture/win/retry + snakes roll-to-100/retry');
  await page.close();
 }
}finally{await browser?.close();server.close();}

