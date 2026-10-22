// NIGHT Play piano-note movement: actual multi-frame browser input & geometry.
// This catches tile teleport on taps, stuttering hold tails, and out-of-order
// melody skipping. Run with Playwright under the project's shared preview code.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import {dirname,resolve,join,extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const visual=join(root,'visual-output','piano-tiles');mkdirSync(visual,{recursive:true});
const assets=new Set(['records.js','game-shell.css','piano-tiles/index.html','piano-tiles/style.css',
 'piano-tiles/game.js','piano-tiles/piano-audio.js','piano-tiles/song-library.js','piano-tiles/full-scores.js',
 ...['C3','Fs3','A3','C4','Ds4','Fs4','A4','C5','Ds5','Fs5'].map(n=>'piano-tiles/samples/'+n+'.mp3')]);
const contentType={'.js':'text/javascript','.html':'text/html','.css':'text/css','.mp3':'audio/mpeg'};
const server=createServer((req,res)=>{
 let name=new URL(req.url,'http://localhost').pathname.slice(1);
 if(name==='piano-tiles/'||name==='piano-tiles')name='piano-tiles/index.html';
 if(!assets.has(name)){res.writeHead(404);res.end();return}
 const buf=readFileSync(join(root,name));
 res.writeHead(200,{'content-type':contentType[extname(name)],'content-length':buf.length});res.end(buf);
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const base='http://127.0.0.1:'+server.address().port;
let browser;
async function launchPage(song){
 const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 await page.goto(base+'/piano-tiles/',{waitUntil:'domcontentloaded'});
 await page.locator('[data-play="'+(song||'endless')+'"]').tap();
 await page.waitForFunction(()=>document.body.classList.contains('is-playing'));
 return page;
}
const state=p=>p.evaluate(()=>({s:window.__nightPianoTest.snapshot(),g:window.__nightPianoTest.geometry()}));
async function tapNote(page,idx,holdMillis=0){
 const d=await state(page),box=await page.locator('#board').boundingBox();
 const n=d.s.notes[idx],g=d.g[idx];
 const cx=box.x+(n.lane+.5)*box.width/4;
 const cy=Math.max(7,Math.min(653,g.bottom-9,Math.max(38,g.top+25)));
 const y=box.y+cy*box.height/660;
 const cdp=await page.context().newCDPSession(page);
 const id=idx+4;
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id,x:cx,y}]});
 if(holdMillis)await page.waitForTimeout(holdMillis);
 if(holdMillis)return {cdp,id,cy,x:cx,y,g,n};
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 return {g,n};
}
try{
 browser=await chromium.launch({headless:true,args:['--no-sandbox','--autoplay-policy=no-user-gesture-required']});
 const p=await launchPage();
 await p.waitForTimeout(80);
 await p.screenshot({path:join(visual,'piano-falling-390.png')});
 const before=await state(p);
 assert.ok(before.g[0].bottom>210&&before.g[0].bottom<420,
  'first note should already be descending within the active playing area');
 const future=24;
 assert.equal(before.s.complete,0);
 const clicked=await tapNote(p,0);
 await p.waitForTimeout(100);
 const after=await state(p);
 assert.equal(after.s.complete,1,'first real touch triggers correct next note');
 // The far-away note must move only by the integrated global scroll distance,
 // not retroactively jump after a player gains points.
 const expected=after.s.distance-before.s.distance;
 const actual=after.g[future].bottom-before.g[future].bottom;
 assert.ok(Math.abs(actual-expected)<.9,
  'future note teleported after score: expected travel '+expected+', got '+actual);
 for(let t of [1,2,3,4,5]){
  await p.waitForTimeout(70);
  const next=await state(p);
  const calc=567+next.s.distance-
   await p.evaluate(at=>window.__nightPianoTest.travelAt(at),next.s.notes[future].at);
  assert.ok(Math.abs(next.g[future].bottom-calc)<.05,'continuous beat position at frame '+t);
  assert.ok(next.g[future].bottom>after.g[future].bottom,'future note falls DOWN frame '+t);
 }
 console.log('PASS tap cannot teleport other falling tiles');
 await p.close();
 const melodic=await launchPage('frere');
 // Touch each upcoming note when its full tile is genuinely visible. This
 // uses the same physical touchscreen events as a human.
 let heldCandidate=-1;
 for(let steps=0;steps<12;steps++){
  let d=await state(melodic);
  let idx=d.s.notes.findIndex(n=>n.state==='waiting');
  if(idx<0)throw Error('song ended before a hold');
  if(d.s.notes[idx].hold){heldCandidate=idx;break;}
  let retries=0;
  while(d.g[idx].bottom<105 && retries++<120){
   await melodic.waitForTimeout(40);
   d=await state(melodic);
  }
  assert.ok(d.g[idx].bottom>=105,'next note must have entered screen');
  await tapNote(melodic,idx);
  const afterTap=await state(melodic);
  assert.ok(!afterTap.s.ended,'normal note press did not kill the song '+idx);
  assert.equal(afterTap.s.notes[idx].state,'done','normal note '+idx+' finished');
 }
 assert.ok(heldCandidate>=0,'first Frere Jacques long note reached');
 let d=await state(melodic),retries=0;
 while(d.g[heldCandidate].bottom<115&&retries++<130){
  await melodic.waitForTimeout(40);d=await state(melodic);
 }
 const handle=await tapNote(melodic,heldCandidate,80);
 const holding1=await state(melodic);
 assert.equal(holding1.s.notes[heldCandidate].state,'holding');
 const tailStart=holding1.g[heldCandidate].tailLength;
 await melodic.waitForTimeout(220);
 const holding2=await state(melodic);
 await melodic.screenshot({path:join(visual,'piano-long-note-held-390.png')});
 const tailEnd=holding2.g[heldCandidate].tailLength;
 assert.ok(tailEnd<tailStart-30,'hold-tail did not contract smoothly '+tailStart+' '+tailEnd);
 assert.ok(tailEnd>138,'hold tail collapsed too quickly');
 assert.ok(Math.abs(holding2.g[heldCandidate].bottom-holding1.g[heldCandidate].bottom)<.1,
   'long-tile finger anchor drifted');
 await handle.cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 await melodic.waitForTimeout(50);
 const released=await state(melodic);
 assert.equal(released.s.notes[heldCandidate].state,'done','partial long note completes on release');
 assert.equal(released.s.ended,false,'early release must NOT unfairly end song');
 console.log('PASS anchored moving long-note ribbon; partial hold allowed');
 await melodic.close();
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
