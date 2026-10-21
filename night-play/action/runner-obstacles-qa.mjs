// NIGHT Play Endless Runner — SVG art plus genuine key input/collision regression.
// Run: node night-play/action/runner-obstacles-qa.mjs after installing Playwright.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const output=join(root,'visual-output');mkdirSync(output,{recursive:true});
const names=['flyer-up','flyer-down','glider','pillar','stone','barrel'];
const allowed=new Map([
 ['/action/','action/index.html'],['/action/games.js','action/games.js'],
 ['/action/style.css','action/style.css'],['/game-shell.css','game-shell.css'],
 ...names.map(name=>['/action/art/'+name+'.svg','action/art/'+name+'.svg'])
]);
const fixture=`window.__runnerQaInject=(kind,x=220)=>{
 selectGame('runner');start();state.spawn=99;state.player.x=83;
 state.obstacles=[{kind,x,w:kind==='flying'?48:kind==='glider'?51:kind==='pillar'?29:kind==='stone'?43:36,
 h:kind==='pillar'?54:kind==='stone'?28:kind==='barrel'?39:kind==='flying'?48:58}];
 state.clock=.4;paused=true;render();};\n`;
const src=readFileSync(join(root,'action/games.js'),'utf8');
assert.ok(src.includes('window.__nightActionTest='),'test fixture insertion point');
const qaJs=src.replace('window.__nightActionTest=',fixture+'window.__nightActionTest=');
const server=createServer((req,res)=>{
 const pathname=new URL(req.url,'http://127.0.0.1').pathname;
 const file=allowed.get(pathname);
 if(!file){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'content-type':file.endsWith('.svg')?'image/svg+xml':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(file==='action/games.js'?qaJs:readFileSync(join(root,file)));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const origin='http://127.0.0.1:'+server.address().port;
const snap=p=>p.evaluate(()=>window.__nightActionTest.snapshot());
let browser;
try{
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 for(const [width,height] of [[320,568],[390,844],[412,915],[1280,850]]){
  const page=await browser.newPage({viewport:{width,height},hasTouch:true,isMobile:width<700});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const artResponses=[];page.on('response',res=>{if(res.url().endsWith('.svg'))artResponses.push(res.status());});
  await page.goto(origin+'/action/?game=runner',{waitUntil:'networkidle'});
  const art=await page.evaluate(names=>Promise.all(names.map(name=>new Promise(done=>{
    const image=new Image();image.onload=()=>done({name,ok:!!image.naturalWidth});
    image.onerror=()=>done({name,ok:false});image.src='/action/art/'+name+'.svg';
   }))),names);
  assert.ok(art.every(asset=>asset.ok),'all six runner sprites decode: '+JSON.stringify(art));
  assert.ok(artResponses.every(code=>code===200),'SVG response status '+JSON.stringify(artResponses));
  assert.deepEqual(errors,[],'no JS exceptions');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)<=1,'no horizontal overflow '+width);
  if(width===390){
   for(const kind of ['flying','glider','pillar','stone','barrel']){
    await page.evaluate(kind=>window.__runnerQaInject(kind,250),kind);
    await page.locator('#screen').screenshot({path:join(output,'runner-'+kind+'-390.png')});
   }
  }
  await page.close();
 }
 const page=await browser.newPage({viewport:{width:390,height:844},hasTouch:true,isMobile:true});
 await page.goto(origin+'/action/?game=runner',{waitUntil:'networkidle'});
 await page.locator('#overlayStart').tap();await page.waitForTimeout(5700);
 assert.equal((await snap(page)).over,true,'doing nothing eventually hits an obstacle');
 // The high drone is high enough to crouch under, using a held REAL key.
 await page.evaluate(()=>window.__runnerQaInject('glider',220));
 await page.keyboard.down('ArrowDown');await page.locator('#pauseButton').tap();
 await page.waitForTimeout(1030);assert.equal((await snap(page)).over,false,'duck clears high flyer');
 assert.ok((await snap(page)).passed>=1,'high flyer has passed');
 await page.keyboard.up('ArrowDown');
 // A low flyer still strikes a crouched player, so its hazard is meaningful.
 await page.evaluate(()=>window.__runnerQaInject('flying',220));
 await page.keyboard.down('ArrowDown');await page.locator('#pauseButton').tap();
 await page.waitForTimeout(1030);assert.equal((await snap(page)).over,true,'duck cannot dodge low flyer');
 await page.keyboard.up('ArrowDown');
 // Timing the jump at the proper moment must clear the exact same sprite.
 await page.evaluate(()=>window.__runnerQaInject('flying',160));
 await page.locator('#pauseButton').tap();await page.keyboard.press('ArrowUp');
 await page.waitForTimeout(1030);assert.equal((await snap(page)).over,false,'timed real jump clears low flyer');
 assert.ok((await snap(page)).passed>=1,'low flyer has passed');
 await page.close();
 console.log('PASS: runner visual assets and responsive real-input hazards at 320/390/412/1280px');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
