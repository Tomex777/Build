import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const files={'/piano-tiles/':'piano-tiles/index.html','/piano-tiles':'piano-tiles/index.html',
 '/piano-tiles/game.js':'piano-tiles/game.js','/piano-tiles/style.css':'piano-tiles/style.css',
 '/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const source=files[new URL(req.url,'http://localhost').pathname];
 if(!source){res.writeHead(404);res.end();return;}
 const mime=source.endsWith('.js')?'text/javascript':source.endsWith('.css')?'text/css':'text/html';
 res.writeHead(200,{'content-type':mime});res.end(readFileSync(path.join(root,source)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const url='http://127.0.0.1:'+server.address().port+'/piano-tiles/';
const output=path.join(root,'piano-tiles','visual-output');
mkdirSync(output,{recursive:true});
let browser;
try{
 browser=await chromium.launch({headless:true,channel:process.env.NIGHT_TEST_CHROME_CHANNEL||'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(url);
 await page.locator('#board').waitFor();
 assert.equal(await page.title(),'Piano Tiles · NIGHT Play');
 assert.equal(await page.locator('.night-brand-mark use').getAttribute('href'),'#night-mark');
 for(const [w,h] of [[320,568],[360,740],[390,844],[412,915],[1024,768]]){
  await page.setViewportSize({width:w,height:h});
  const m=await page.locator('#board').evaluate(el=>{
   const b=el.getBoundingClientRect();
   return {x:b.x,right:b.right,width:b.width,height:b.height,overflow:document.documentElement.scrollWidth};
  });
  assert.ok(m.width>=200,'Four portrait lanes must remain playable: '+w+': '+JSON.stringify(m));
  assert.ok(Math.abs(m.height/m.width-660/400)<.02,'No canvas distortion: '+JSON.stringify(m));
  assert.ok(m.x>=0&&m.right<=w+1&&m.overflow<=w+2,'No horizontal overflow: '+JSON.stringify(m));
  if(w===320||w===390||w===1024)
   writeFileSync(path.join(output,'piano-start-'+w+'.png'),await page.screenshot({fullPage:true}));
 }
 await page.setViewportSize({width:390,height:844});
 await page.locator('#coverAction').click();
 await page.waitForFunction(()=>window.__nightPianoTest.snapshot().elapsed>.13);
 await page.locator('#pause').click();
 const before=await page.evaluate(()=>window.__nightPianoTest.snapshot().elapsed);
 await page.waitForTimeout(200);
 const after=await page.evaluate(()=>window.__nightPianoTest.snapshot().elapsed);
 assert.ok(Math.abs(after-before)<.01,'Pause actually freezes musical timing');
 await page.locator('#pause').click();
 assert.equal(await page.locator('#cover').isHidden(),true);
 const first=await page.evaluate(()=>window.__nightPianoTest.snapshot().notes[0]);
 await page.waitForFunction(at=>window.__nightPianoTest.snapshot().elapsed>=at-.13,first.at);
 let bb=await page.locator('#board').boundingBox();
 const x=bb.x+(first.lane+.5)*bb.width/4,y=bb.y+(660-93-40)*bb.height/660;
 await page.mouse.click(x,y);
 let state=await page.evaluate(()=>window.__nightPianoTest.snapshot());
 assert.equal(state.complete,1,'Live pointer click actually hits a timed piano tile: '+JSON.stringify(state));
 assert.ok(state.score>=100,'Piano tap produces real score');
 writeFileSync(path.join(output,'piano-play-tap.png'),await page.screenshot({fullPage:true}));
 await page.mouse.click(bb.x+bb.width*.9,bb.y+bb.height*.83);
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().ended),true,
  'Tapping an empty lane causes a real loss');
 assert.equal(await page.locator('#coverTitle').textContent(),'Wrong tile');
 await page.locator('#coverAction').click();
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().ended),false,
  'Restart must create a fresh playable round');
 const events=await page.evaluate(()=>window.__nightPianoTest.snapshot().notes);
 assert.ok(events.some(n=>n.hold&&n.duration>.6),'Arrangement includes genuine extended hold notes');
 assert.ok(events.some((n,i)=>i>0&&events.some((other,j)=>j!==i&&other.at===n.at)),
  'Arrangement contains real simultaneous notes');
 const holds=[];
 for(let i=0;i<events.length;i++){
  const event=events[i],key=String(event.lane+1);
  await page.waitForFunction(at=>window.__nightPianoTest.snapshot().elapsed>=at-.033,event.at,
   {timeout:10000});
  for(let j=holds.length-1;j>=0;j--)if(holds[j].until<=event.at-.05){
   await page.keyboard.up(holds[j].key);holds.splice(j,1);
  }
  if(event.hold){
   await page.keyboard.down(key);
   holds.push({key,until:event.at+event.duration+.10});
  }else await page.keyboard.press(key);
  state=await page.evaluate(()=>window.__nightPianoTest.snapshot());
  assert.equal(state.ended,false,'Must survive a properly timed note '+i+' at '+event.at);
  if(i===8){
   assert.ok(state.complete>=8,'Held notes and their next simultaneous taps both count');
   writeFileSync(path.join(output,'piano-long-hold.png'),await page.screenshot({fullPage:true}));
  }
 }
 const last=events.at(-1);
 await page.waitForFunction(at=>window.__nightPianoTest.snapshot().ended||
    window.__nightPianoTest.snapshot().elapsed>=at+.15,last.at+last.duration,{timeout:10000});
 for(const h of holds)await page.keyboard.up(h.key);
 state=await page.evaluate(()=>window.__nightPianoTest.snapshot());
 assert.equal(state.ended,true,'A complete musical chart reaches a genuine win condition');
 assert.equal(state.complete,events.length,'Winning requires every note, including chords and holds');
 assert.equal(await page.locator('#coverKicker').textContent(),'SONG COMPLETE');
 assert.ok(state.score>=events.length*100,'Both taps and sustained notes score properly');
 writeFileSync(path.join(output,'piano-song-won.png'),await page.screenshot({fullPage:true}));

 // Wave 2: a complete second melody, an actual phone touch, early/late misses,
 // and a full second song victory. Song switching must not reset a live match.
 await page.locator('[data-song="twinkle"]').click();
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().song),'twinkle');
 assert.equal(await page.locator('[data-song="twinkle"]').getAttribute('aria-pressed'),'true');
 await page.locator('#coverAction').click();
 assert.equal(await page.locator('[data-song="frere"]').isDisabled(),true);
 const firstTwinkle=await page.evaluate(()=>window.__nightPianoTest.snapshot().notes[0]);
 await page.waitForFunction(at=>window.__nightPianoTest.snapshot().elapsed>=at-.34,firstTwinkle.at);
 let touchBox=await page.locator('#board').boundingBox();
 await page.touchscreen.tap(touchBox.x+(firstTwinkle.lane+.5)*touchBox.width/4,
   touchBox.y+(660-93-40)*touchBox.height/660);
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().ended),true,
  'Pressing before the first note is actually tappable must lose');
 assert.equal(await page.locator('#coverTitle').textContent(),'Wrong tile');
 await page.locator('#coverAction').click();
 await page.waitForFunction(()=>window.__nightPianoTest.snapshot().ended);
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().ended),true,
  'Missing a note after the late timing window must lose');
 assert.equal(await page.locator('#coverTitle').textContent(),'Missed tile');
 await page.locator('#coverAction').click();
 let second=await page.evaluate(()=>window.__nightPianoTest.snapshot());
 assert.equal(second.song,'twinkle');
 assert.ok(second.notes.length>=42,'The second full song has a real playable chart');
 assert.ok(second.notes.some(n=>n.hold),'The second song has long notes');
 assert.ok(second.notes.some((n,i)=>second.notes.some((other,j)=>i!==j&&other.at===n.at)),
  'The second song has playable harmonized chords');
 const otherHolds=[];
 for(let i=0;i<second.notes.length;i++){
  const note=second.notes[i],key=String(note.lane+1);
  await page.waitForFunction(at=>window.__nightPianoTest.snapshot().elapsed>=at-.027,
    note.at,{timeout:12000});
  for(let j=otherHolds.length-1;j>=0;j--)if(otherHolds[j].until<=note.at-.05){
   await page.keyboard.up(otherHolds[j].key);otherHolds.splice(j,1);
  }
  if(i===0){
   touchBox=await page.locator('#board').boundingBox();
   await page.touchscreen.tap(touchBox.x+(note.lane+.5)*touchBox.width/4,
     touchBox.y+(660-93-40)*touchBox.height/660);
  }else if(note.hold){
   await page.keyboard.down(key);
   otherHolds.push({key,until:note.at+note.duration+.09});
  }else await page.keyboard.press(key);
  const progress=await page.evaluate(()=>window.__nightPianoTest.snapshot());
  assert.equal(progress.ended,false,'Second complete song must survive note '+i);
  if(i===12||i===30)writeFileSync(path.join(output,'twinkle-progress-'+i+'.png'),
   await page.screenshot({fullPage:true}));
 }
 const final=second.notes.at(-1);
 await page.waitForFunction(at=>window.__nightPianoTest.snapshot().ended||
   window.__nightPianoTest.snapshot().elapsed>=at+.20,final.at+final.duration,{timeout:12000});
 for(const key of otherHolds)await page.keyboard.up(key.key);
 second=await page.evaluate(()=>window.__nightPianoTest.snapshot());
 assert.equal(second.complete,second.notes.length,'All second-song notes and chords count');
 assert.equal(await page.locator('#coverKicker').textContent(),'SONG COMPLETE');
 writeFileSync(path.join(output,'twinkle-song-won.png'),await page.screenshot({fullPage:true}));
 await page.locator('[data-song="frere"]').click();
 assert.equal(await page.evaluate(()=>window.__nightPianoTest.snapshot().song),'frere');
 assert.equal(await page.locator('#best').textContent(),String(state.score),
  'The second song does not overwrite the first song’s high score');

 assert.deepEqual(errors,[],'No script exceptions while tapping, holding, winning and restarting');
 console.log('PASS NIGHT Piano Tiles: two complete melodies, actual touch, early/late losses, holds/chords, wins/restart, pause, 5 viewports; screenshots '+output);
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
