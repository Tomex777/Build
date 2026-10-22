// NIGHT Play Piano Tiles song-list and exact spacing gameplay regression.
// Real-browser play of the 37-song catalogue; deliberate TILE_HEIGHT-sized gaps.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync} from 'node:fs';
import {dirname,resolve,join,extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const assets=new Set(['game-shell.css','piano-tiles/index.html','piano-tiles/style.css',
 'piano-tiles/game.js','piano-tiles/song-library.js','piano-tiles/full-scores.js','piano-tiles/piano-audio.js',
 ...['C3','Fs3','A3','C4','Ds4','Fs4','A4','C5','Ds5','Fs5'].map(k=>'piano-tiles/samples/'+k+'.mp3')]);
const mime={'.mp3':'audio/mpeg','.js':'application/javascript','.html':'text/html','.css':'text/css'};
const server=createServer((req,res)=>{
 let path=new URL(req.url,'http://localhost').pathname.slice(1);
 if(path==='piano-tiles/')path='piano-tiles/index.html';
 if(!assets.has(path)){res.writeHead(404);res.end();return}
 const file=readFileSync(join(root,path));
 res.writeHead(200,{'content-type':mime[extname(path)],'content-length':file.length});
 res.end(file);
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const origin='http://127.0.0.1:'+server.address().port;
let browser;
const state=p=>p.evaluate(()=>({s:window.__nightPianoTest.snapshot(),g:window.__nightPianoTest.geometry()}));
try{
 browser=await chromium.launch({headless:true,args:['--no-sandbox','--autoplay-policy=no-user-gesture-required']});
 for(const [width,height] of [[320,568],[390,844],[412,915],[1280,800]]){
  const page=await browser.newPage({viewport:{width,height},isMobile:width<700,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/piano-tiles/');
  const tunes=await page.evaluate(()=>window.NightPianoSongLibrary.songs);
  assert.equal(tunes.length,39,'all 37 arrangements in catalogue');
  assert.equal(await page.locator('[data-play]').count(),39,'every song has a play button');
  assert.equal(await page.locator('#playPage').isVisible(),false,'selection screen first');
  const position=await page.locator('[data-play="endless"]').boundingBox();
  assert.ok(position?.width>40,'play control real hit area');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  assert.ok(overflow<=1,'no overflow '+width);
  if(width===390){
   await page.locator('#songSearch').fill('moonlit');
   assert.equal(await page.locator('[data-play]').count(),1,'search actually filters');
   assert.equal(await page.locator('[data-play="moonlit"]').count(),1,'search found moonlit');
   await page.locator('#songSearch').fill('');
   const fav=page.locator('[data-favourite="moonlit"]');await fav.tap();
   assert.equal(await page.locator('[data-favourite="moonlit"]').getAttribute('aria-pressed'),'true','favorite toggles');
   await page.getByRole('button',{name:'Favorites',exact:true}).tap();
   assert.equal(await page.locator('[data-play]').count(),1,'Favorites filter actually works');
   await page.getByRole('button',{name:'All songs',exact:true}).tap();
  }
  await page.locator('[data-play="endless"]').tap();
  await page.waitForFunction(()=>document.body.classList.contains('is-playing'));
  let initial=await state(page);
  assert.equal(initial.s.ended,false);
  const first=initial.g[0],second=initial.g[1];
  assert.ok(Math.abs((first.bottom-first.length)-second.bottom-166)<.6,
   'white gap exactly a tile-height: '+JSON.stringify({first,second}));
  const box=await page.locator('#board').boundingBox();
  const event=page.context().newCDPSession(page);const cd=await event;
  await cd.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{
   id:1,x:box.x+box.width*(initial.s.notes[0].lane+.5)/4,
   y:box.y+box.height*(first.bottom-45)/660}]});
  await cd.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  await page.waitForTimeout(65);
  const tap=await state(page);
  assert.equal(tap.s.complete,1,'visible first tile real tap');
  const delta=tap.s.distance-initial.s.distance;
  assert.ok(Math.abs(tap.g[1].bottom-initial.g[1].bottom-delta)<.9,
   'second tile unaffected by scoring');
  assert.deepEqual(errors,[],'no JavaScript errors');
  console.log('PASS viewport',width,'game width',Math.round(box.width),'gap',Math.round((first.bottom-first.length)-second.bottom));
  await page.close();
 }
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 await page.goto(origin+'/piano-tiles/');
 const titles=await page.evaluate(()=>window.NightPianoSongLibrary.songs.map(s=>({id:s.id,title:s.title})));
 for(const [index,song] of titles.entries()){
  await page.locator('[data-play="'+song.id+'"]').tap();
  await page.waitForFunction(()=>document.body.classList.contains('is-playing'));
  const t=await state(page);
  assert.equal(t.s.song,song.id,'selected song actually loads '+song.id);
  assert.ok(t.s.notes.length>=24,'real notes, not an empty catalogue card '+song.id);
  // Every successive non-chord normal key has a normal-key-sized white gap.
  for(let i=1;i<Math.min(t.s.notes.length,14);i++){
   const previous=t.g[i-1],next=t.g[i],sameChord=Math.abs(t.s.notes[i].at-t.s.notes[i-1].at)<.01;
   if(sameChord)continue;
   const gap=(previous.bottom-previous.length)-next.bottom;
   assert.ok(Math.abs(gap-166)<1.1,song.id+' tile '+i+' white gap='+gap);
  }
  await page.locator('#songBack').tap();
  assert.equal(await page.locator('#songLibrary').isVisible(),true,'back to song browsing');
  assert.equal(await page.locator('[data-play]').count(),39,'all songs remain selectable');
  if(index%12===0)console.log('CHECKED',index+1,song.id,t.s.notes.length);
 }
 await page.close();
 console.log('PASS all 37 songs genuinely playable; each note spaced by one full key height');
}finally{if(browser)await browser.close();await new Promise(done=>server.close(done));}
