import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/piano-tiles/':'piano-tiles/index.html','/piano-tiles/game.js':'piano-tiles/game.js','/piano-tiles/style.css':'piano-tiles/style.css','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const rel=routes[new URL(req.url,'http://localhost').pathname];if(!rel){res.writeHead(404);res.end();return;}
 const body=readFileSync(path.join(root,rel));res.writeHead(200,{'content-type':rel.endsWith('.js')?'text/javascript':rel.endsWith('.css')?'text/css':'text/html'});res.end(body);
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
const url='http://127.0.0.1:'+server.address().port+'/piano-tiles/';
const out=path.join(root,'piano-tiles','visual-output');mkdirSync(out,{recursive:true});
let browser;
try{
 browser=await chromium.launch({headless:true,channel:process.env.NIGHT_TEST_CHROME_CHANNEL||'chrome',args:['--no-sandbox']});
 for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[1024,768]]){
  const page=await browser.newPage({viewport:{width,height},isMobile:width<700,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url);await page.locator('#coverAction').click();
  await page.waitForFunction(()=>window.__nightPianoTest.snapshot().elapsed>.28,null,{timeout:5000});
  const frame=await page.evaluate(()=>{
   const b=document.querySelector('.board-shell').getBoundingClientRect();
   const c=document.querySelector('#board').getBoundingClientRect();
   return {width:b.width,height:b.height,canvasWidth:c.width,canvasHeight:c.height,
    right:b.right,bottom:b.bottom,innerWidth,innerHeight,
    scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight};
  });
  if(width<700){
   assert.ok(frame.width>=width-12, 'Active Piano Tiles must fill usable phone width, not a narrow fixed aspect '+width);
   assert.ok(frame.canvasWidth/4>=70,'Each playable lane stays finger-sized on '+width);
  }else assert.ok(frame.width>=490,'Desktop playfield remains useful width');
  assert.ok(frame.bottom<=height,'Board fits viewport height '+width);
  assert.ok(frame.scrollWidth<=width+1,'No sideways scrolling '+width);
  assert.ok(frame.scrollHeight<=height+1,'No vertical scroll steals gameplay '+width);
  await page.screenshot({path:path.join(out,'A0-ACTIVE-FRAME-'+width+'.png')});
  // Actual browser touch, not a synthetic game.js hit call.
  await page.waitForFunction(()=>{
   const s=window.__nightPianoTest.snapshot();const g=window.__nightPianoTest.geometry()[0];
   return s.notes[0]&&!s.notes[0].hold&&g&&g.bottom>60&&g.top<660;
  },null,{timeout:6000});
  const note=await page.evaluate(()=>({tile:window.__nightPianoTest.snapshot().notes[0],g:window.__nightPianoTest.geometry()[0]}));
  const rect=await page.locator('#board').boundingBox();
  const px=rect.x+(note.tile.lane+.5)*rect.width/4;
  const py=rect.y+Math.max(18,Math.min(620,note.g.bottom-22))*rect.height/660;
  await page.touchscreen.tap(px,py);
  assert.ok((await page.evaluate(()=>window.__nightPianoTest.snapshot().complete))>=1,'Full visible tile touch registers at '+width);
  assert.deepEqual(errors,[],'No browser JS exceptions at '+width);
  console.log('PASS Piano active layout + real touch '+width+'x'+height+' board '+Math.round(frame.width)+'x'+Math.round(frame.height));
  await page.close();
 }
}finally{await browser?.close();server.close();}

