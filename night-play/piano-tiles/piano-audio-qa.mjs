// NIGHT Play — real grand-piano sample and touch-play regression.
// Run with: node night-play/piano-tiles/piano-audio-qa.mjs (requires playwright).
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync, mkdirSync} from 'node:fs';
import {dirname, resolve, extname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const output=join(root,'visual-output','piano-tiles');mkdirSync(output,{recursive:true});
const assets=new Set([
 'piano-tiles/index.html','piano-tiles/style.css','piano-tiles/game.js',
 'piano-tiles/piano-audio.js','game-shell.css',
 ...['C3','Fs3','A3','C4','Ds4','Fs4','A4','C5','Ds5','Fs5'].map(n=>'piano-tiles/samples/'+n+'.mp3')
]);
const mime={'.js':'text/javascript','.html':'text/html','.css':'text/css','.mp3':'audio/mpeg'};
const server=createServer((req,res)=>{
 let name=new URL(req.url,'http://localhost').pathname.slice(1);
 if(name==='piano-tiles/'||name==='piano-tiles')name='piano-tiles/index.html';
 if(!assets.has(name)){res.writeHead(404);res.end();return}
 const bytes=readFileSync(join(root,name));
 res.writeHead(200,{'content-type':mime[extname(name)]||'application/octet-stream',
  'content-length':bytes.length,'cache-control':'public, max-age=31536000, immutable'});
 res.end(bytes);
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
const origin='http://127.0.0.1:'+server.address().port;
const opts={headless:true,args:['--no-sandbox','--autoplay-policy=no-user-gesture-required']};
try{
 browser=await chromium.launch(opts);
 for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[1280,800]]){
  const p=await browser.newPage({viewport:{width,height},isMobile:width<700,hasTouch:true});
  const errors=[],samples=[];
  p.on('pageerror',e=>errors.push(e.message));
  p.on('response',r=>{if(r.url().endsWith('.mp3'))samples.push(r.status())});
  await p.goto(origin+'/piano-tiles/',{waitUntil:'domcontentloaded'});
  assert.ok(await p.locator('.piano-attribution').isVisible(),'required sample credit visible');
  assert.equal(await p.locator('[data-song]').count(),3,'song library still present');
  await p.locator('#coverAction').tap();
  await p.waitForFunction(()=>document.body.classList.contains('is-playing'),null,{timeout:30000});
  const instrument=await p.evaluate(()=>window.NightPiano.inspect());
  assert.equal(instrument.loaded,10,'ten locally decoded grand-piano notes');
  assert.equal(instrument.contextState,'running','mobile Web Audio resumed on gesture');
  assert.ok(Object.values(instrument.durations).every(n=>n>2&&n<=3.7),'bounded decoded sample memory');
  assert.ok(samples.every(x=>x===200)&&samples.length===10,'every local MP3 served');
  const rect=await p.locator('#board').boundingBox();
  assert.ok(rect.width>=300&&rect.height>=350,'large playable touch area');
  if(width===390)await p.screenshot({path:join(output,'piano-real-grand-390.png')});
  await p.touchscreen.tap(rect.x+rect.width*.125,rect.y+rect.height*.115);
  await p.waitForTimeout(110);
  const strike=await p.evaluate(()=>({
   info:window.NightPiano.inspect(),
   score:Number(document.getElementById('score').textContent)
  }));
  assert.ok(strike.score>=100,'first visible tile responds to one real tap');
  assert.ok(strike.info.activeVoices>=1,'sampled note actually playing');
  assert.equal(strike.info.lastPlayed.key,'C4','first tone comes from real recorded C4');
  if(width===390){
   await p.waitForTimeout(850);
   assert.ok((await p.evaluate(()=>window.NightPiano.inspect().activeVoices))>=1,
    'short piano tone naturally rings after tile completes');
   const held=await p.evaluate(()=>window.NightPiano.play(440,true));
   await p.waitForTimeout(100);
   await p.evaluate(()=>window.NightPiano.silence());
  }
  await p.locator('#pause').tap();await p.waitForTimeout(160);
  assert.equal((await p.evaluate(()=>window.NightPiano.inspect())).activeVoices,0,
   'pausing fades and ends all samples');
  const overflow=await p.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  assert.ok(overflow<=1,'no horizontal overflow');
  assert.deepEqual(errors,[],'no JS errors');
  console.log('PASS',width, 'piano voices',samples.length,'board',Math.round(rect.width)+'×'+Math.round(rect.height));
  await p.close();
 }
 // Both actual public-domain finite melodies must trigger their first notes.
 for(const id of ['frere','twinkle']){
  const p=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
  await p.goto(origin+'/piano-tiles/');
  await p.locator('[data-song="'+id+'"]').tap();await p.locator('#coverAction').tap();
  await p.waitForFunction(()=>document.body.classList.contains('is-playing'));
  const rect=await p.locator('#board').boundingBox();
  await p.waitForTimeout(650); // finite charts enter the visible lanes later
  await p.touchscreen.tap(rect.x+rect.width*.125,rect.y+rect.height*.11);
  await p.waitForTimeout(50);
  assert.ok((await p.evaluate(()=>window.NightPiano.inspect())).lastPlayed,'song played sample');
  console.log('PASS song',id);await p.close();
 }
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
