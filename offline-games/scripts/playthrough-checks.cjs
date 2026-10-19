#!/usr/bin/env node
/* Play-to-outcome browser scenarios for visually identified high-risk games.
 * Unlike single-input catalog smoke, these play through win conditions.
 */
'use strict';
const {chromium}=require('playwright-core'),fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const root=path.resolve(__dirname,'../app/src/main/assets'),out=path.resolve(__dirname,'../qa-evidence/playthrough');
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg'};
const server=http.createServer((req,res)=>{const name=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!name.startsWith(root+path.sep)){res.writeHead(403);res.end();return}fs.readFile(name,(e,b)=>{if(e){res.writeHead(404);res.end();return}res.writeHead(200,{'content-type':mime[path.extname(name)]||'text/plain'});res.end(b)})});
async function main(){
 fs.mkdirSync(out,{recursive:true});await new Promise(res=>server.listen(0,'127.0.0.1',res));const port=server.address().port;
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const cases=[];let failed=0;
 async function run(id,fn){
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});const errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto('http://127.0.0.1:'+port+'/classics/index.html?game='+encodeURIComponent(id),{waitUntil:'load',timeout:20000});
  await fn(page);
  if(errors.length)throw new Error('Runtime: '+errors.join('; '));
  await page.screenshot({path:path.join(out,id+'-completed.png'),fullPage:false});
  cases.push({id,pass:true,message:(await page.locator('#status').innerText()).slice(0,180)});
  console.log('PLAYTHROUGH_PASS '+id);
 }catch(e){failed++;cases.push({id,pass:false,error:String(e).slice(0,500)});console.log('PLAYTHROUGH_FAIL '+id+' '+String(e).slice(0,500));await page.screenshot({path:path.join(out,id+'-FAILED.png'),fullPage:false}).catch(()=>{})}
 finally{await page.close()}
 }
 const assert=(ok,msg)=>{if(!ok)throw new Error(msg)};
 const cell=(page,i)=>page.locator('#board .tiles .cell').nth(i);
 await run('wordsearch',async page=>{
  for(const word of ['NIGHT','GAME','PUZZLE','SNAKE','BOARD','PLAY']){
   const data=await page.evaluate(word=>{
    const chars=[...document.querySelectorAll('#board .tiles .cell')].map(e=>e.textContent.trim());
    if(chars.length!==81)throw new Error('Word Search grid missing');
    for(let y=0;y<9;y++)for(let x=0;x<9;x++)for(const [dx,dy] of [[1,0],[0,1],[1,1],[1,-1],[-1,0],[0,-1],[-1,-1],[-1,1]]){
     const coord=Array.from({length:word.length},(_,i)=>[x+i*dx,y+i*dy]);
     if(coord.every(([a,b],k)=>a>=0&&a<9&&b>=0&&b<9&&chars[b*9+a]===word[k]))return [y*9+x,coord.at(-1)[1]*9+coord.at(-1)[0]];
    }
    throw new Error('Word cannot be found: '+word);
   },word);
   await cell(page,data[0]).click();await cell(page,data[1]).click();
  }
  const status=await page.locator('#status').innerText();
  assert(status.includes('All six words found'),'Wordsearch not won: '+status);
 });
 await run('mazerunner',async page=>{
  const route=await page.evaluate(()=>{
   const elements=[...document.querySelectorAll('#board .tiles .cell')];
   if(elements.length!==169)throw new Error('Maze grid missing');
   const pass=i=>elements[i].style.background!=='rgb(48, 78, 63)';
   let queue=[14],prev=new Map([[14,null]]);
   while(queue.length){const i=queue.shift();if(i===154)break;const x=i%13,y=Math.floor(i/13);
    for(const [dx,dy,key] of [[1,0,'ArrowRight'],[-1,0,'ArrowLeft'],[0,1,'ArrowDown'],[0,-1,'ArrowUp']]){
     const nx=x+dx,ny=y+dy,j=ny*13+nx;
     if(nx<0||nx>=13||ny<0||ny>=13||prev.has(j)||!pass(j))continue;
     prev.set(j,[i,key]);queue.push(j)
    }
   }
   if(!prev.has(154))throw new Error('No path to goal');
   const result=[];for(let cur=154;cur!==14;){const [parent,key]=prev.get(cur);result.push(key);cur=parent}return result.reverse();
  });
  for(const key of route)await page.keyboard.press(key);
  const status=await page.locator('#status').innerText();
  assert(status.includes('Maze escaped'),'Maze does not report win: '+status);
 });
 await run('bomb',async page=>{
  const names=await page.locator('.bomb-step').allInnerTexts();
  assert(names.length===3,'Bomb missing 3-step circuit instructions');
  for(const name of names)await page.getByRole('button',{name:'Cut '+name.toLowerCase()+' wire'}).click();
  assert((await page.locator('#status').innerText()).includes('safely disarmed'),'Bomb success not reported');
 });
 await run('memory',async page=>{
   const pairs=await page.evaluate(()=>{
      const cards=[...document.querySelectorAll('.memory-card img')];
      if(cards.length!==24)throw new Error('Memory default 24 cards missing');
      const seen=new Map(),pairs=[];
      for(let i=0;i<cards.length;i++){const name=cards[i].getAttribute('src');if(seen.has(name))pairs.push([seen.get(name),i]);else seen.set(name,i)}
      return pairs
   });
   assert(pairs.length===12,'Memory cards are not 12 pairs');
   for(const [i,j] of pairs){await page.locator('.memory-card').nth(i).click();await page.locator('.memory-card').nth(j).click()}
   assert((await page.locator('#status').innerText()).includes('All 12 pairs'),'Memory win condition missing');
 });
 await run('chess',async page=>{
   await page.locator('button[aria-label^="e2 "]').click();
   await page.locator('button[aria-label^="e4 "]').click();
   await page.waitForTimeout(800);
   const moved=await page.locator('button[aria-label^="e4 "]').getAttribute('aria-label');
   assert(moved.includes('White p'),'e2-e4 move failed: '+moved);
   assert((await page.locator('#board .cell').count())===64,'Chess board not 64 cells');
 });
 await run('checkers',async page=>{
   const from=40,to=33;
   await cell(page,from).click();await cell(page,to).click();
   assert((await page.locator('#board .cell').count())===64,'Checkers board vanished');
   const moved=await cell(page,to).getAttribute('data-active');
   assert(moved==='true','Expected legal opening move 40->33 to be occupied by dark piece, got '+moved);
 });
 await run('swimming',async page=>{
   for(let i=0;i<22;i++){if((await page.locator('#status').innerText()).includes('You win'))break;await page.getByRole('button',{name:'Stroke'}).click();await page.waitForTimeout(390)}
   const status=await page.locator('#status').innerText();assert(status.includes('You win'),'Swimming 50m not reached: '+status);
 });
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(cases,null,2));await browser.close();server.close();
 console.log('PLAYTHROUGH_TOTALS '+JSON.stringify({cases:cases.length,passed:cases.length-failed,failed}));
 if(failed)process.exitCode=1;
}
main().catch(e=>{console.error(e);server.close();process.exitCode=2});
