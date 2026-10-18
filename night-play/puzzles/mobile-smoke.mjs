import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const fileMap={'/puzzles/':'puzzles/index.html','/puzzles':'puzzles/index.html',
 '/puzzles/core.js':'puzzles/core.js','/puzzles/games.js':'puzzles/games.js',
 '/puzzles/style.css':'puzzles/style.css','/game-shell.css':'game-shell.css',
 '/games':'index.html'};
const server=createServer((req,res)=>{
 const file=fileMap[new URL(req.url,'http://localhost').pathname];
 if(!file){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 const url='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 const saved=async kind=>page.evaluate(k=>JSON.parse(localStorage.getItem('night-play-puzzle-room-v1-'+k)),kind);
 const folder=path.join(root,'visual-output');mkdirSync(folder,{recursive:true});
 async function checkMobile(){
  const metrics=await page.locator('#board').evaluate(el=>{const r=el.getBoundingClientRect();return {left:r.left,right:r.right,width:r.width,pageWidth:document.documentElement.scrollWidth,viewport:innerWidth,header:document.querySelector('.night-shell-header').getBoundingClientRect().width};});
  assert.ok(metrics.width>=285,'board uses the phone width '+JSON.stringify(metrics));
  assert.ok(metrics.left>=0&&metrics.right<=metrics.viewport+1,'board stays within phone screen');
  assert.ok(metrics.pageWidth<=metrics.viewport+2,'no horizontal scrolling');
 }
 await page.goto(url+'/puzzles/?game=sudoku');
 assert.equal(await page.locator('#title').innerText(),'Sudoku');
 assert.equal(await page.locator('.sudoku-cell').count(),81);
 await checkMobile();
 assert.equal(await page.locator('.night-brand-mark use').getAttribute('href'),'#night-mark');
 let sudoku=await saved('sudoku'),index=sudoku.board.indexOf(0);
 const wrong=(sudoku.solution[index]%9)+1;
 await page.locator('[data-cell="'+index+'"]').click();
 await page.locator('[data-number="'+wrong+'"]').click();
 assert.equal((await saved('sudoku')).mistakes,1,'wrong entry adds a mistake');
 assert.equal(await page.locator('[data-cell="'+index+'"].conflict').count(),1);
 await page.locator('[data-action="erase"]').click();
 await page.locator('[data-action="notes"]').click();
 await page.locator('[data-number="5"]').click();
 assert.ok((await saved('sudoku')).notes[index].includes('5'));
 await page.locator('[data-action="notes"]').click();
 await page.locator('[data-number="'+sudoku.solution[index]+'"]').click();
 assert.equal((await saved('sudoku')).board[index],sudoku.solution[index]);
 await page.reload();
 assert.equal((await saved('sudoku')).mistakes,1,'Sudoku restores game');
 await page.locator('#settingsButton').click();
 assert.equal(await page.locator('[data-puzzle="memory"]').isVisible(),true);
 await page.locator('[data-option="hard"]').click();
 assert.equal((await saved('sudoku')).level,'hard');
 assert.equal(await page.locator('.sudoku-cell').count(),81);
 await page.screenshot({path:path.join(folder,'puzzle-sudoku-mobile.jpg'),type:'jpeg',quality:75});
 // Solve every remaining square through the actual on-screen cell and number handlers.
 sudoku=await saved('sudoku');
 await page.evaluate(solution=>{
  for(let i=0;i<81;i++){
   if(document.querySelector('[data-cell="'+i+'"]').classList.contains('given'))continue;
   document.querySelector('[data-cell="'+i+'"]').click();
   document.querySelector('[data-number="'+solution[i]+'"]').click();
  }
 },sudoku.solution);
 assert.equal((await saved('sudoku')).done,true);
 assert.match(await page.locator('#message').innerText(),/Puzzle complete/);
 await page.goto(url+'/puzzles/?game=memory');
 assert.equal(await page.locator('#title').innerText(),'Memory Match');
 assert.equal(await page.locator('.memory-theme-option').count(),5,'five original themed collections');
 assert.equal(await page.locator('.memory-size-option').count(),10,'sizes grow downward to 6 × 10');
 assert.equal(await page.locator('.memory-card').count(),0,'pick cards before a fresh game');
 assert.equal(await page.locator('.memory-start').count(),1);
 await checkMobile();
 await page.waitForTimeout(700);
 await page.screenshot({path:path.join(folder,'memory-setup-mobile.jpg'),type:'jpeg',quality:70,fullPage:true});
 console.log('NIGHT_MEMORY_SETUP_IMAGE_BASE64='+(await page.screenshot({type:'jpeg',quality:48})).toString('base64'));
 // A 6×5 board: 6 columns across, 5 rows down. The entire board should fit a normal phone.
 await page.locator('[data-memory-size="6x5"]').click();
 await page.locator('[data-memory-theme="wildlife"]').click();
 await page.locator('[data-memory-action="start"]').click();
 let mem=await saved('memory');
 // The v2 Memory engine owns a separate persistence key, not the older puzzle-room namespace.
 assert.equal(await page.locator('.memory-card').count(),30);
 assert.equal(await page.locator('.memory-playing-board').evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),6);
 const fit=await page.evaluate(()=>{
  const board=document.getElementById('board').getBoundingClientRect();
  const action=document.getElementById('newGame').getBoundingClientRect();
  return {boardWidth:board.width,boardHeight:board.height,left:board.left,right:board.right,bottom:board.bottom,actionTop:action.top,screenWidth:innerWidth,screenHeight:innerHeight};
 });
 assert.ok(fit.boardWidth>=370,'6×5 takes the phone width: '+JSON.stringify(fit));
 assert.ok(fit.bottom<=fit.screenHeight+2,'6×5 full board fits phone without shrinking cards: '+JSON.stringify(fit));
 assert.ok(fit.actionTop>=fit.bottom-1,'restart sits below the board');
 await page.waitForTimeout(1000);
 console.log('NIGHT_MEMORY_PLAY_6x5_IMAGE_BASE64='+(await page.screenshot({type:'jpeg',quality:54})).toString('base64'));
 await page.screenshot({path:path.join(folder,'memory-play-6x5-mobile.jpg'),type:'jpeg',quality:72,fullPage:true});
 mem=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')));
 assert.equal(mem.theme,'wildlife');
 assert.equal(mem.cols,6);assert.equal(mem.rows,5);
 const first=mem.deck[0],mate=mem.deck.findIndex((x,i)=>i>0&&x===first);
 await page.locator('[data-memory="0"]').click();
 await page.locator('[data-memory="'+mate+'"]').click();
 mem=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')));
 assert.equal(mem.matched.filter(Boolean).length,2);
 let unmatched=mem.deck.map((v,i)=>({v,i})).filter(x=>x.v!==first);
 const x=unmatched[0],y=unmatched.find(p=>p.v!==x.v);
 await page.locator('[data-memory="'+x.i+'"]').click();
 await page.locator('[data-memory="'+y.i+'"]').click();
 assert.equal(await page.locator('.memory-card.flipped').count(),2);
 await page.waitForTimeout(950);
 assert.equal(await page.locator('.memory-card.flipped').count(),0,'mismatches flip back smoothly');
 await page.reload();
 assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')))).matched.filter(Boolean).length,2,'matched cards survive reload');
 mem=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')));
 for(const id of new Set(mem.deck)){
  const indices=mem.deck.map((v,i)=>v===id?i:-1).filter(i=>i>=0);
  if(mem.matched[indices[0]])continue;
  await page.locator('[data-memory="'+indices[0]+'"]').click();
  await page.locator('[data-memory="'+indices[1]+'"]').click();
 }
 assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')))).done,true,'all 15 pairs can be completed');
 assert.match(await page.locator('#message').innerText(),/All 15 pairs found/);
 // Changing a deck should not erase an in-progress game until Start is pressed.
 await page.locator('.memory-deck-btn').click();
 assert.equal(await page.locator('.memory-theme-option').count(),5);
 await page.locator('[data-memory-theme="storybook"]').click();
 await page.locator('[data-memory-size="6x10"]').click();
 await page.locator('[data-memory-action="start"]').click();
 mem=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')));
 assert.equal(mem.theme,'storybook');
 assert.equal(mem.deck.length,60,'six columns and TEN rows (60 cards)');
 assert.equal(new Set(mem.deck).size,30,'30 distinct original storybook characters');
 for(const [width,height] of [[320,568],[390,844],[412,915],[540,760],[768,1024]]){
  await page.setViewportSize({width,height});
  const m=await page.locator('#board').evaluate(el=>{
   const r=el.getBoundingClientRect(),cards=[...el.querySelectorAll('.memory-card')].map(c=>c.getBoundingClientRect());
   return {left:r.left,right:r.right,width:r.width,height:r.height,pageWidth:document.documentElement.scrollWidth,viewport:innerWidth,rows:new Set(cards.map(c=>Math.round(c.top))).size,
    cardWidth:cards[0].width,cardHeight:cards[0].height};
  });
  assert.ok(m.left>=0&&m.right<=width+1&&m.pageWidth<=width+2,'no side overflow at '+width+'×'+height+': '+JSON.stringify(m));
  assert.equal(m.rows,10,'10 actual rows at '+width+'×'+height);
  assert.ok(m.cardWidth>=42,'even 320px supports readable faces at '+width+'×'+height+': '+JSON.stringify(m));
  assert.ok(m.height>=m.cardHeight*10,'tall boards scroll vertically, not shrink');
 }
 await page.setViewportSize({width:390,height:844});
 await page.screenshot({path:path.join(folder,'memory-play-6x10-mobile.jpg'),type:'jpeg',quality:72,fullPage:true});
 console.log('NIGHT_MEMORY_PLAY_6x10_IMAGE_BASE64='+(await page.screenshot({type:'jpeg',quality:54})).toString('base64'));
 await page.locator('.memory-deck-btn').click();
 assert.equal(await page.locator('[data-memory-action="resume"]').count(),0,'no resume action before any turns');
 // A brief touch to the larger board persists independently of its vertical scroll.
 await page.locator('[data-memory-action="start"]').click();
 await page.locator('[data-memory="59"]').click();
 const savedCards=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-memory-v2')));
 assert.equal(savedCards.deck.length,60);
 await page.reload();
 assert.equal(await page.locator('.memory-card').count(),60,'large board resumes after reload');
 await page.goto(url+'/puzzles/?game=connect');
 assert.equal(await page.locator('.connect-col').count(),7);
 assert.equal((await saved('connect')).mode,'cpu');
 await page.locator('[data-column="3"]').click();
 await page.waitForTimeout(500);
 let connect=await saved('connect');
 assert.equal(connect.cells.flat().filter(Boolean).length,2,'the CPU responds to a human move');
 assert.equal(connect.turn,1);
 await page.locator('#settingsButton').click();
 await page.locator('[data-option="local"]').click();
 assert.equal((await saved('connect')).mode,'local');
 for(const col of [0,1,0,1,0,1,0])await page.locator('[data-column="'+col+'"]').click();
 connect=await saved('connect');
 assert.equal(connect.ended,1,'four coral pieces in a column must end the match');
 const before=JSON.stringify(connect.cells);
 await page.locator('[data-column="2"]').evaluate(el=>el.click());
 assert.equal(JSON.stringify((await saved('connect')).cells),before,'finished board cannot accept further moves');
 await page.reload();
 assert.equal((await saved('connect')).ended,1,'finished Connect Four matches restore');
 await page.screenshot({path:path.join(folder,'puzzle-connect-mobile.jpg'),type:'jpeg',quality:75});
 for(const game of ['sudoku','memory','connect']){
  await page.goto(url+'/puzzles/?game='+game);
  for(const [width,height] of [[320,568],[390,844],[540,760],[768,1024]]){
   await page.setViewportSize({width,height});
   const m=await page.locator('#board').evaluate(el=>{const r=el.getBoundingClientRect();return {left:r.left,right:r.right,pageWidth:document.documentElement.scrollWidth,viewport:innerWidth}});
   assert.ok(m.left>=0&&m.right<=m.viewport+1&&m.pageWidth<=m.viewport+2,'no horizontal overflow '+game+' '+width+'×'+height+': '+JSON.stringify(m));
  }
 }
 assert.deepEqual(errors,[],'no browser exceptions');
 console.log('Puzzle room Chromium smoke passed: Sudoku, memory, CPU and local four-in-a-row, persistence, 320–768 widths.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
