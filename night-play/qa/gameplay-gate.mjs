/* NIGHT Play independent regression gate.
 * Real Chromium touch swipes on phones, mouse swipes on desktop, game snapshots, and visual captures across
 * narrow phones and desktop. Passing this gate is NOT a human visual sign-off.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const mapping={
 '/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css',
 '/puzzles/':'puzzles/index.html','/puzzles/style.css':'puzzles/style.css',
 '/puzzles/core.js':'puzzles/core.js','/puzzles/memory-art.js':'puzzles/memory-art.js',
 '/puzzles/memory.js':'puzzles/memory.js','/puzzles/games.js':'puzzles/games.js',
 '/games':'index.html'
};
const server=createServer((req,res)=>{
 const pathname=new URL(req.url,'http://127.0.0.1').pathname;
 const name=mapping[pathname];
 if(!name){res.writeHead(404);res.end('No QA fixture: '+pathname);return;}
 try{
  const data=readFileSync(path.join(root,name));
  res.writeHead(200,{'content-type':name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':'text/html'});
  res.end(data);
 }catch(e){res.writeHead(404);res.end(String(e))}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const base='http://127.0.0.1:'+server.address().port;
const output=path.join(root,'visual-output');
mkdirSync(output,{recursive:true});
let browser;
const results=[], errors=[];
const sizes=[
 {width:320,height:740},{width:360,height:780},{width:390,height:844},
 {width:412,height:915},{width:1280,height:900}
];
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function actualTouchSwipe(page,a,b){
 const cdp=await page.context().newCDPSession(page);
 const id=11;
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:a.x,y:a.y,id}]});
 for(let i=1;i<=7;i++){
  const fraction=i/7;
  await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{
   x:a.x+(b.x-a.x)*fraction,y:a.y+(b.y-a.y)*fraction,id
  }]});
  await sleep(12);
 }
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 await cdp.detach();
}
async function actualMouseSwipe(page,a,b){
 await page.mouse.move(a.x,a.y);
 await page.mouse.down();
 await page.mouse.move(b.x,b.y,{steps:8});
 await page.mouse.up();
}
async function evidence(page,name){
 await page.screenshot({path:path.join(output,name+'.jpg'),type:'jpeg',quality:75});
}
async function noOverflow(page,description){
 const m=await page.evaluate(()=>({
  content:document.documentElement.scrollWidth,
  viewport:innerWidth,
  canvas:document.getElementById('screen')?.getBoundingClientRect().toJSON()
 }));
 assert.ok(m.content<=m.viewport+2,description+' horizontal overflow: '+JSON.stringify(m));
 if(m.canvas)assert.ok(m.canvas.left>=-1&&m.canvas.right<=m.viewport+1,description+' canvas out of bounds');
 return m;
}
const snapshot=page=>page.evaluate(()=>window.__nightActionTest.snapshot());
try{
 browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox']});
 for(const {width,height} of sizes){
  const isMobile=width<600;
  const page=await browser.newPage({viewport:{width,height},isMobile,hasTouch:true,deviceScaleFactor:isMobile?2:1});
  page.on('pageerror',error=>errors.push(width+': '+String(error)));
  // Force the same reproducible fruit type and launch lane at every size.
  await page.addInitScript(()=>{Math.random=()=>.5});
  await page.goto(base+'/action/?game=slice');
  await noOverflow(page,'Fruit Slice '+width);
  await page.locator('#overlayStart').click();
  await page.waitForFunction(()=>window.__nightActionTest.snapshot().fruits.some(f=>f.kind!=='bomb'&&f.y>70&&f.y<440),
    null,{timeout:6500,polling:25});
  const fruit=(await snapshot(page)).fruits.find(f=>f.kind!=='bomb'&&f.y>70&&f.y<440);
  assert.ok(fruit,'fruit within slicing area at '+width);
  const box=await page.locator('canvas').boundingBox();
  const p=(x,y)=>({x:box.x+x/390*box.width,y:box.y+y/550*box.height});
  // Swipe through empty space first. A gesture alone MUST NOT award points.
  await (isMobile?actualTouchSwipe:actualMouseSwipe)(page,p(12,45),p(105,45));
  assert.equal((await snapshot(page)).score,0,'off-fruit real touch swipe cannot score');
  await evidence(page,'qa-fruit-before-'+width);
  // Find fruit again because gravity continues during swipe and screenshot.
  const current=(await snapshot(page)).fruits.find(f=>f.kind!=='bomb'&&f.y>35&&f.y<500);
  assert.ok(current,'fruit still on screen before cut');
  await (isMobile?actualTouchSwipe:actualMouseSwipe)(page,p(current.x-39,current.y),p(current.x+39,current.y));
  const cut=await snapshot(page);
  assert.equal(cut.score,10,'one real touch crossing earns exactly ten at '+width);
  assert.equal(cut.halves.length,2,'actual cut replaces fruit with two halves at '+width);
  assert.ok(cut.splashes.length>=12,'cut causes visible particles');
  await evidence(page,'qa-fruit-cut-'+width);
  await page.waitForTimeout(110);
  const apart=await snapshot(page);
  assert.ok(Math.hypot(apart.halves[0].x-apart.halves[1].x,apart.halves[0].y-apart.halves[1].y)>12,
   'fruit halves must visibly separate at '+width);
  await evidence(page,'qa-fruit-separated-'+width);
  results.push({game:'slice',width,score:cut.score,halves:apart.halves.length,offTargetScore:0});
  await page.close();

  const memory=await browser.newPage({viewport:{width,height},isMobile,hasTouch:true});
  memory.on('pageerror',error=>errors.push('memory '+width+': '+String(error)));
  await memory.goto(base+'/puzzles/?game=memory');
  const m=await memory.evaluate(()=>({viewport:innerWidth,pageWidth:document.documentElement.scrollWidth}));
  assert.ok(m.pageWidth<=m.viewport+2,'Memory Match no horizontal overflow '+width);
  await memory.locator('#settingsButton').click();
  assert.equal(await memory.locator('[data-memory-size="6x5"]').getAttribute('aria-pressed'),'true',
   'new player memory defaults to six columns and five rows');
  await evidence(memory,'qa-memory-settings-'+width);
  results.push({game:'memory-settings',width,overflow:m.pageWidth-m.viewport});
  await memory.close();
 }
 // Independently verify that ignoring a flying obstacle in Runner actually
 // ends the run; pause/restart is exercised via the visible UI.
 {
  const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  page.on('pageerror',error=>errors.push('runner: '+String(error)));
  await page.addInitScript(()=>{Math.random=()=>.18});
  await page.goto(base+'/action/?game=runner');
  await page.locator('#overlayStart').click();
  await page.waitForFunction(()=>window.__nightActionTest.snapshot().over,null,{timeout:10000});
  assert.equal((await snapshot(page)).over,true,'idle runner must eventually collide and lose');
  await evidence(page,'qa-runner-collision-390');
  await page.locator('#restart').click();
  assert.equal((await snapshot(page)).over,false,'restart clears game-over');
  await evidence(page,'qa-runner-restart-390');
  results.push({game:'runner',width:390,deadWhenIdle:true,restart:true});
  await page.close();
 }
 assert.deepEqual(errors,[],'no browser errors across QA scenarios');
 console.log('NIGHT_PLAY_REAL_TOUCH_QA='+JSON.stringify(results));
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
