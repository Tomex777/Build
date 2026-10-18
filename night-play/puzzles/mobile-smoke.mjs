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
 // Finish a nearly completed valid save by entering the final number on the real UI.
 sudoku=await saved('sudoku');index=sudoku.board.indexOf(0);
 await page.evaluate(i=>{const key='night-play-puzzle-room-v1-sudoku';const s=JSON.parse(localStorage.getItem(key));s.board=s.solution.slice();s.board[i]=0;s.givens=s.board.slice();s.done=false;localStorage.setItem(key,JSON.stringify(s));},index);
 await page.reload();
 await page.locator('[data-cell="'+index+'"]').click();
 await page.locator('[data-number="'+sudoku.solution[index]+'"]').click();
 assert.equal((await saved('sudoku')).done,true);
 assert.match(await page.locator('#message').innerText(),/Puzzle complete/);
 await page.goto(url+'/puzzles/?game=memory');
 assert.equal(await page.locator('.memory-card').count(),16);
 let mem=await saved('memory');
 const first=mem.deck[0],mate=mem.deck.findIndex((x,i)=>i>0&&x===first);
 await page.locator('[data-memory="0"]').click();
 await page.locator('[data-memory="'+mate+'"]').click();
 assert.equal((await saved('memory')).matched.filter(Boolean).length,2);
 const unmatched=mem.deck.map((v,i)=>({v,i})).filter(x=>x.v!==first);
 const x=unmatched[0],y=unmatched.find(p=>p.v!==x.v);
 await page.locator('[data-memory="'+x.i+'"]').click();await page.locator('[data-memory="'+y.i+'"]').click();
 assert.equal(await page.locator('.memory-card.flipped').count(),2);
 await page.waitForTimeout(780);
 assert.equal(await page.locator('.memory-card.flipped').count(),0,'mismatches flip back');
 await page.reload();
 assert.equal((await saved('memory')).matched.filter(Boolean).length,2,'matched cards survive reload');
 mem=await saved('memory');
 for(const id of new Set(mem.deck)){
  const indices=mem.deck.map((value,i)=>value===id?i:-1).filter(i=>i>=0);
  if(mem.matched[indices[0]])continue;
  await page.locator('[data-memory="'+indices[0]+'"]').click();
  await page.locator('[data-memory="'+indices[1]+'"]').click();
 }
 assert.equal((await saved('memory')).done,true,'all eight pairs can be completed');
 assert.match(await page.locator('#message').innerText(),/All pairs found/);
 await page.screenshot({path:path.join(folder,'puzzle-memory-mobile.jpg'),type:'jpeg',quality:75});
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
