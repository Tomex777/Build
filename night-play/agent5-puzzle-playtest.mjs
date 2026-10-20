// Agent 5 hands-on puzzle regression: real taps, swipes, keyboard, victory/loss,
// persistence, accessible card identities and recorded mobile viewport evidence.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.dirname(fileURLToPath(import.meta.url));
const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
const files={
 '/site-shell.css':'site-shell.css','/account-client.js':'account-client.js',
 '/game-shell.css':'game-shell.css',
 '/puzzles/core.js':'puzzles/core.js',
 '/puzzles/memory-art.js':'puzzles/memory-art.js',
 '/puzzles/memory.js':'puzzles/memory.js',
 '/puzzles/games.js':'puzzles/games.js',
 '/puzzles/style.css':'puzzles/style.css'
};
const server=createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/api/account'){
  res.writeHead(200,{'content-type':'application/json'});
  res.end(JSON.stringify({linked:false,profile:null}));return;
 }
 const filename=files[pathname]||(pathname.startsWith('/puzzles')?'puzzles/index.html':'index.html');
 try{
  const body=readFileSync(path.join(root,filename));
  const type=filename.endsWith('.js')?'text/javascript':filename.endsWith('.css')?'text/css':'text/html';
  res.writeHead(200,{'content-type':type,'cache-control':'no-store'});res.end(body);
 }catch(error){res.writeHead(404);res.end(String(error));}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const origin='http://127.0.0.1:'+server.address().port;
 const errors=[];
 const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true});
 page.on('pageerror',error=>errors.push(String(error)));
 await page.route('https://images.unsplash.com/**',route=>route.abort());
 await page.goto(origin+'/puzzles/?game=memory');
 assert.equal(await page.locator('#title').innerText(),'Memory Match');
 assert.equal(await page.locator('.memory-theme-option').count(),5);
 await page.screenshot({path:path.join(output,'agent5-memory-setup-390.jpg'),quality:70,type:'jpeg'});
 await page.locator('[data-memory-action="start"]').click();
 assert.equal(await page.locator('.memory-card').count(),30,'6 × 5 starts with 15 real pairs');
 const board=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')));
 assert.equal(board.cols,6);assert.equal(board.rows,5);
 assert.equal(new Set(board.deck).size,15);
 const label=await page.evaluate(id=>window.NIGHTMemoryArt.photos.wildlife[id][0],board.deck[0]);
 assert.equal(await page.locator('[data-memory="0"]').getAttribute('aria-label'),'Card 1, face down','hide unknown card before turning');
 const firstRect=await page.locator('[data-memory="0"]').boundingBox();
 assert.ok(firstRect,'first card visible on mobile');
 await page.touchscreen.tap(firstRect.x+firstRect.width/2,firstRect.y+firstRect.height/2);
 assert.equal(await page.locator('[data-memory="0"]').getAttribute('aria-label'),'Card 1, revealed: '+label,'a real mobile tap reveals an identifiable face');
 const wrong=board.deck.findIndex(id=>id!==board.deck[0]);
 await page.locator('[data-memory="'+wrong+'"]').click();
 assert.equal(await page.locator('.memory-card.flipped').count(),2,'mismatch briefly exposes two cards');
 assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')))).moves,1,'one mismatch means one attempt');
 await page.waitForTimeout(980);
 assert.equal(await page.locator('.memory-card.flipped').count(),0,'mismatched faces turn back');
 const mate=board.deck.findIndex((id,i)=>id===board.deck[0]&&i!==0);
 await page.locator('[data-memory="0"]').click();
 await page.locator('[data-memory="'+mate+'"]').click();
 assert.match(await page.locator('[data-memory="0"]').getAttribute('aria-label'),new RegExp('matched: '+label));
 assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')))).matched.filter(Boolean).length,2);
 await page.reload();
 assert.equal(await page.locator('.memory-card.matched').count(),2,'found pairs survive a reload');
 // Finish all remaining pairs through DOM click/tap paths rather than mutating saved state.
 for(const value of new Set(board.deck)){
  const indices=board.deck.map((id,i)=>id===value?i:-1).filter(i=>i!==-1);
  if(indices.includes(0))continue;
  await page.locator('[data-memory="'+indices[0]+'"]').click();
  await page.locator('[data-memory="'+indices[1]+'"]').click();
 }
 assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')))).done,true);
 assert.match(await page.locator('#message').innerText(),/All 15 pairs found/);
 // Wait for the actual 3D flips before documenting the final visual state.
 await page.waitForTimeout(420);
 assert.equal(await page.locator('.memory-card.matched .memory-flip-body').count(),30);
 await page.screenshot({path:path.join(output,'agent5-memory-finished-390.jpg'),quality:73,type:'jpeg'});
 console.log('AGENT5_MEMORY_PLAYED '+JSON.stringify({size:'6x5',pairs:15,wrongTurns:1,voiceLabels:true,restored:true,victory:true}));

 // Real 2048 game input and milestone feedback, not only checking a helper.
 const tiles=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true});
 tiles.on('pageerror',error=>errors.push(String(error)));
 await tiles.goto(origin+'/play/2048');
 await tiles.locator('#board2048 .tile').first().waitFor();
 const beforeWin=[
  [1024,1024,0,0],
  [2,4,8,16],
  [16,8,4,2],
  [2,4,8,16]
 ];
 const seed=async(grid,score=0)=>tiles.evaluate(({grid,score})=>{
  localStorage.setItem('night-play-setting-2048-size','4');
  localStorage.setItem('night-play-save-2048-4',JSON.stringify({grid,size:4,score,updatedAt:Date.now()}));
 },{grid,score});
 await seed(beforeWin);
 await tiles.reload();
 assert.equal(await tiles.locator('#board2048[data-outcome="playing"]').count(),1);
 await tiles.keyboard.press('ArrowLeft');
 await tiles.locator('#board2048[data-outcome="won"]').waitFor();
 assert.equal(await tiles.locator('#board2048 .tile[data-v="2048"]').count(),1);
 assert.match(await tiles.locator('#gameHelp').textContent(),/you won/);
 assert.equal(await tiles.locator('#score').textContent(),'2048','1024 + 1024 has an exact 2048 score consequence');
 // The board intentionally animates for 185ms. Do not capture a half-moving
 // 1024 ghost and misrepresent the completed 2048 tile in visual evidence.
 await tiles.locator('#board2048:not(.tile-sliding)').waitFor();
 await tiles.screenshot({path:path.join(output,'agent5-2048-win-390.jpg'),quality:74,type:'jpeg'});
 await tiles.locator('#undo2048').click();
 assert.equal(await tiles.locator('#board2048[data-outcome="playing"]').count(),1,'undo correctly reverts the winning merge');
 // Exercise actual synthesized touchscreen gesture over the playfield.
 const rect=await tiles.locator('#board2048').boundingBox();
 assert.ok(rect,'2048 board visible on phone');
 const y=rect.y+rect.height*.45,startX=rect.x+rect.width*.80,endX=rect.x+rect.width*.18;
 const cdp=await tiles.context().newCDPSession(tiles);
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:3,x:startX,y,radiusX:5,radiusY:5}]});
 for(let i=1;i<=6;i++)await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{id:3,x:startX+(endX-startX)*i/6,y,radiusX:5,radiusY:5}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await cdp.detach();
 await tiles.locator('#board2048[data-outcome="won"]').waitFor();
 assert.equal(await tiles.locator('#score').textContent(),'2048','touch swipe also creates the winning merge');

 const stuck=[[2,4,2,4],[4,2,4,2],[2,4,2,4],[4,2,4,2]];
 await seed(stuck,548);
 await tiles.reload();
 await tiles.locator('#board2048[data-outcome="lost"]').waitFor();
 assert.match(await tiles.locator('#gameHelp').textContent(),/game over/,'reloaded unsolvable board visibly signals loss');
 assert.equal(await tiles.locator('#score').textContent(),'548');
 await tiles.screenshot({path:path.join(output,'agent5-2048-loss-390.jpg'),quality:74,type:'jpeg'});
 await seed([[4096,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],4096);
 await tiles.reload();
 assert.equal(await tiles.locator('.tile[data-v="4096"][data-high="true"]').count(),1,'4096 and larger tiles stay legible');
 assert.equal(await tiles.locator('#board2048[data-outcome="won"]').count(),1);
 await seed([[3,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],-12);
 await tiles.reload();
 assert.equal(await tiles.locator('.tile[data-v="3"]').count(),0,'invalid scores and impossible tiles are rejected');
 console.log('AGENT5_2048_PLAYED '+JSON.stringify({milestone:true,winScore:2048,undo:true,touchSwipe:true,loss:true,highTiles:true,badSaveRejected:true}));

 // The settings also offer 5×5 and 6×6. Play a genuine merge on each,
 // including undo and persisted score. Never mistake a selectable setting
 // for verified gameplay, particularly at the most cramped phone width.
 for(const size of [5,6]){
  const savedGrid=Array.from({length:size},(_,row)=>
   Array.from({length:size},(_,col)=>row===0&&col<2?2:0));
  await tiles.evaluate(({size,grid})=>{
   localStorage.setItem('night-play-setting-2048-size',String(size));
   localStorage.setItem('night-play-save-2048-'+size,JSON.stringify({size,grid,score:0,updatedAt:Date.now()}));
  },{size,grid:savedGrid});
  await tiles.reload();
  assert.equal(await tiles.locator('#board2048 .tile').count(),size*size,'correct live board dimensions for '+size+'×'+size);
  await tiles.keyboard.press('ArrowLeft');
  assert.equal(await tiles.locator('#score').textContent(),'4','real merge earns 4 on '+size+'×'+size);
  assert.equal(await tiles.locator('#board2048 .tile[data-v="4"]').count(),1);
  await tiles.locator('#undo2048').click();
  assert.equal(await tiles.locator('#score').textContent(),'0','undo works on '+size+'×'+size);
  await tiles.keyboard.press('ArrowLeft');
  await tiles.reload();
  assert.equal(await tiles.locator('#score').textContent(),'4','score resumes on '+size+'×'+size);
  const geom=await tiles.evaluate(()=>({viewport:innerWidth,scrollWidth:document.documentElement.scrollWidth}));
  assert.ok(geom.scrollWidth<=geom.viewport+2,'no '+size+'×'+size+' horizontal overflow: '+JSON.stringify(geom));
  await tiles.screenshot({path:path.join(output,'agent5-2048-'+size+'x'+size+'-390.jpg'),quality:73,type:'jpeg'});
 }
 console.log('AGENT5_2048_ALL_SIZES_PLAYED '+JSON.stringify({sizes:['4x4','5x5','6x6'],moves:true,undo:true,resume:true}));

 // Small phones still fit both games without horizontal scrolling.
 const narrow=await browser.newPage({viewport:{width:320,height:640},deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await narrow.route('https://images.unsplash.com/**',route=>route.abort());
 await narrow.goto(origin+'/puzzles/?game=memory');
 await narrow.locator('[data-memory-action="start"]').click();
 for(const p of [narrow,tiles]){
  const layout=await p.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth}));
  assert.ok(layout.scrollWidth<=layout.width+2,'no horizontal overflow: '+JSON.stringify(layout));
 }
 await narrow.screenshot({path:path.join(output,'agent5-memory-320.jpg'),quality:70,type:'jpeg'});
 console.log('AGENT5_VISUAL_OUTPUT '+output);
 assert.deepEqual(errors,[],'No unhandled browser errors during real puzzle gameplay');
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
