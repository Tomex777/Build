import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,statSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const out=path.join(root,'visual-output','agent0-chess');
mkdirSync(out,{recursive:true});
const server=createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost');
 let rel=url.pathname;
 if(rel==='/strategy/')rel='/strategy/index.html';
 const full=path.resolve(root,'.'+rel);
 if(!full.startsWith(root+path.sep)){res.writeHead(403);res.end();return}
 try{
  if(!statSync(full).isFile())throw Error('not file');
  const ext=path.extname(full),ctype={'.js':'application/javascript','.css':'text/css','.html':'text/html'};
  res.writeHead(200,{'content-type':ctype[ext]||'application/octet-stream'});
  res.end(readFileSync(full));
 }catch{res.writeHead(404);res.end('missing '+rel)}
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
const outcomes=[];
async function move(page,from,to){
 await page.locator('[data-square="'+from+'"]').click();
 await page.locator('[data-square="'+to+'"]').click();
 await page.waitForFunction(len=>window.__nightChessQA().moves.length>=len,
  (await page.evaluate(()=>window.__nightChessQA().moves.length))+1,{timeout:2000}).catch(()=>{});
}
try{
 for(const width of [390,1280]){
  const page=await browser.newPage({viewport:{width,height:850},isMobile:width<600,hasTouch:width<600});
  const errors=[],sounds=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('response',r=>{if(r.url().includes('/public/sfx/')||r.url().includes('/sfx/'))sounds.push({code:r.status(),url:r.url()})});
  await page.goto(base+'/strategy/');
  const sound=page.locator('#chessSound');
  assert.equal(await sound.getAttribute('aria-pressed'),'true');
  await page.locator('#vsLocal').click();
  const seq=[['f2','f3'],['e7','e5'],['g2','g4'],['d8','h4']];
  for(const [from,to] of seq){
   const pre=await page.evaluate(()=>window.__nightChessQA().moves.length);
   await move(page,from,to);
   await page.waitForFunction(k=>window.__nightChessQA().moves.length===k+1,pre,{timeout:2200});
  }
  assert.equal((await page.evaluate(()=>window.__nightChessQA())).gameOver,true);
  const overlay=page.locator('#chessFinish');
  await overlay.waitFor({state:'visible'});
  assert.equal(await page.locator('#chessFinishTitle').innerText(),'Black wins!');
  const frame=await page.locator('.chess-frame').boundingBox(),result=await overlay.boundingBox();
  assert.ok(result.x>=frame.x-1&&result.x+result.width<=frame.x+frame.width+1,'result must be INSIDE chess board');
  await page.screenshot({path:path.join(out,'chess-black-wins-'+width+'.png'),fullPage:true});
  await page.locator('#chessFinishAgain').click();
  assert.equal(await overlay.isVisible(),false,'restart removes overlay');
  assert.equal((await page.evaluate(()=>window.__nightChessQA().moves.length)),0);
  // Threefold repetition: four reversible knight moves repeated twice.
  const rep=[['g1','f3'],['g8','f6'],['f3','g1'],['f6','g8']];
  for(let i=0;i<2;i++)for(const [from,to] of rep){
   const old=await page.evaluate(()=>window.__nightChessQA().moves.length);
   await move(page,from,to);
   await page.waitForFunction(k=>window.__nightChessQA().moves.length>k,old,{timeout:2200});
  }
  await overlay.waitFor({state:'visible'});
  assert.equal(await page.locator('#chessFinishTitle').innerText(),'Draw');
  assert.equal(await overlay.getAttribute('data-result'),'draw');
  await page.screenshot({path:path.join(out,'chess-draw-'+width+'.png'),fullPage:true});
  await page.locator('#chessFinishAgain').click();
  await sound.click();
  assert.equal(await sound.getAttribute('aria-pressed'),'false');
  const code=await page.evaluate(()=>window.__nightChessQA());
  assert.equal(code.soundOn,false);
  await page.reload();
  assert.equal(await sound.getAttribute('aria-pressed'),'false','sound preference persists');
  assert.deepEqual(errors,[]);
  outcomes.push({width,win:true,draw:true,restart:true,sounds,renderedWithinFrame:true});
  await page.close();
 }
 console.log('AGENT_ZERO_CHESS_REAL_SOUND_AND_ONBOARD_RESULTS '+JSON.stringify(outcomes));
}finally{await browser.close();await new Promise(ok=>server.close(ok));}
