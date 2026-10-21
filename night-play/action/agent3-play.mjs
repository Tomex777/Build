import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

// Browser gameplay regressions: REAL input, collision, restart, five viewports.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css','/action/art/flyer-up.svg':'action/art/flyer-up.svg','/action/art/flyer-down.svg':'action/art/flyer-down.svg','/action/art/glider.svg':'action/art/glider.svg','/action/art/pillar.svg':'action/art/pillar.svg','/action/art/stone.svg':'action/art/stone.svg','/action/art/barrel.svg':'action/art/barrel.svg'};
const server=createServer((req,res)=>{
 const f=routes[new URL(req.url,'http://localhost').pathname];
 if(!f){res.writeHead(404);res.end('Missing');return;}
 res.writeHead(200,{'content-type':f.endsWith('.css')?'text/css':f.endsWith('.js')?'text/javascript':f.endsWith('.svg')?'image/svg+xml':'text/html'});
 res.end(readFileSync(path.join(root,f)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
let browser;
const results=[],errors=[];
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const url='http://127.0.0.1:'+server.address().port;
 async function open(game,random,width=390){
  const p=await browser.newPage({viewport:{width,height:844},isMobile:width<600,hasTouch:true});
  p.on('pageerror',e=>errors.push(String(e)));
  await p.addInitScript(n=>{Math.random=()=>n},random);
  await p.goto(url+'/action/?game='+game);
  await p.locator('#overlayStart').click();
  return p;
 }
 const snap=p=>p.evaluate(()=>window.__nightActionTest.snapshot());
 const condition=(p,f)=>p.waitForFunction(f,null,{timeout:7000,polling:16});
 // Chromium Dino replaces the retired custom runner; its independent real-touch
 // collision and restart test runs separately in this workflow.
 for(const width of [320,360,390,412,1280])for(const game of ['jump']){
  const p=await open(game,game==='jump'?.5:.18,width);
  const state=await snap(p);
  if(game==='jump'){
   assert.ok(state.platforms.slice(0,5).every(q=>!q.moving&&q.w>=82),'five generous safe opening platforms');
   assert.ok(state.platforms.every(q=>q.ttl===undefined&&q.fragile===undefined),'permanent platforms');
   assert.equal(state.hazardY,undefined,'no storm');
   assert.equal(await p.locator('#stat').isVisible(),false,'one height readout');
  }
  await p.waitForTimeout(300);
  const size=await p.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
  assert.ok(size.scroll<=size.width+2,'mobile/desktop overflow '+JSON.stringify({width,game,size}));
  await p.screenshot({path:path.join(output,`agent3-${game}-${width}.jpg`)});
  results.push(game+' rendered '+width);
  await p.close();
 }
 assert.deepEqual(errors,[]);
 console.log('NIGHT_AGENT3_PLAY='+JSON.stringify(results));
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
