// Finite-song touch playthrough. No engine mutations or score setters.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import {resolve,extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=fileURLToPath(new URL('../',import.meta.url));
const server=createServer((req,res)=>{let path=new URL(req.url,'http://localhost').pathname;if(path.endsWith('/'))path+='index.html';const file=resolve(root,'.'+path);if(!file.startsWith(root)){res.writeHead(403);res.end();return}try{const data=readFileSync(file);res.writeHead(200,{'content-type':({'.html':'text/html','.js':'text/javascript','.css':'text/css','.mp3':'audio/mpeg'})[extname(file)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end()}});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const browser=await chromium.launch({headless:true,args:['--no-sandbox','--autoplay-policy=no-user-gesture-required']});
try{
 const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:'+server.address().port+'/piano-tiles/');
 await page.locator('[data-play="frere"]').tap();
 await page.waitForFunction(()=>document.body.classList.contains('is-playing'));
 await page.locator('#pause').tap();
 const before=await page.evaluate(()=>window.__nightPianoTest.snapshot().elapsed);
 await page.waitForTimeout(250);
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().elapsed),before,'pause freezes the actual musical clock');
 await page.locator('#pause').tap();
 const events=await page.evaluate(()=>window.__nightPianoTest.snapshot().notes);
 for(let i=0;i<events.length;i++){
  const note=events[i];
  await page.waitForFunction(i=>{const s=window.__nightPianoTest.snapshot(),g=window.__nightPianoTest.geometry()[i];return !s.ended&&g.bottom>=550},i,{timeout:12000});
  const box=await page.locator('#board').boundingBox();
  const g=await page.evaluate(i=>window.__nightPianoTest.geometry()[i],i);
  const session=await page.context().newCDPSession(page);
  await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:1,x:box.x+(note.lane+.5)*box.width/4,y:box.y+(g.bottom-35)*box.height/660}]});
  if(note.hold)await page.waitForTimeout(note.duration*1000+40);
  await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await session.detach();
  const s=await page.evaluate(()=>window.__nightPianoTest.snapshot());
  assert.ok(!s.ended||s.complete===events.length,'survives correctly played note '+i);
 }
 await page.waitForFunction(()=>window.__nightPianoTest.snapshot().ended);
 assert.equal(await page.locator('#coverKicker').textContent(),'SONG COMPLETE');
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().complete),events.length);
 assert.equal(await page.locator('#restart').isVisible(),false,'one replay action after winning');
 mkdirSync(root+'/piano-tiles/visual-output',{recursive:true});
 await page.screenshot({path:root+'/piano-tiles/visual-output/complete-melody-win.png',fullPage:true});
 await page.locator('#coverAction').tap();
 await page.waitForFunction(()=>window.__nightPianoTest.snapshot().ended,null,{timeout:10000});
 assert.equal(await page.locator('#coverTitle').textContent(),'Missed tile','unplayed tile causes a genuine loss');
 await page.locator('#songBack').tap();assert.equal(await page.locator('[data-play]').count(),39);
 assert.deepEqual(errors,[]);
 console.log('PASS full finite-song touch win, actual long holds, pause, loss, replay and back to 39-song library');
}finally{await browser.close();await new Promise(done=>server.close(done))}
