import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

/* This does NOT count a screenshot at start as a playtest.
   Drive the jumper with actual pointer gestures, react to its live position,
   and observe the real collision engine and early-game survivability. */
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css','/games':'index.html'};
const server=createServer((req,res)=>{
 const file=routes[new URL(req.url,'http://localhost').pathname];
 if(!file){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
const results=[], errors=[];
try{
 const url='http://127.0.0.1:'+server.address().port;
 const dir=path.join(root,'visual-output');mkdirSync(dir,{recursive:true});
 for(const [strategy,steps] of [['steady',135],['steer',160]]){
  const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  page.on('pageerror',e=>errors.push(String(e)));
  await page.goto(url+'/action/?game=jump');
  await page.locator('#overlayStart').click();
  const bounds=await page.locator('#screen').boundingBox();
  let maxScore=0,maxMoving=0,seenFragile=false,collisions=0,lastVy=0,deathStep=-1;
  await page.mouse.move(bounds.x+bounds.width*.46,bounds.y+bounds.height*.65);
  await page.mouse.down();
  for(let i=0;i<steps;i++){
   const s=await page.evaluate(()=>window.__nightActionTest.snapshot());
   maxScore=Math.max(maxScore,s.score);maxMoving=Math.max(maxMoving,s.platforms.filter(p=>p.moving).length);
   if(s.platforms.some(p=>p.fragile))seenFragile=true;
   if(lastVy>0&&s.player.vy<0)collisions++;
   lastVy=s.player.vy;
   if(s.over){deathStep=i;break}
   if(strategy==='steer'){
    const p=s.player;
    // Land on the closest available platform in the current fall path,
    // selecting collision surfaces instead of automating the game engine.
    const choices=s.platforms.filter(q=>q.y>p.y+11&&q.y<p.y+165&&q.ttl>=0);
    choices.sort((a,b)=>{
      const dist=q=>Math.min(Math.abs(q.x+q.w/2-p.x),390-Math.abs(q.x+q.w/2-p.x));
      return (a.y-p.y)*.15+dist(a)*.7-(b.y-p.y)*.15-dist(b)*.7;
    });
    const t=choices[0];
    if(t){const x=Math.min(384,Math.max(6,t.x+t.w/2));
      await page.mouse.move(bounds.x+x/390*bounds.width,bounds.y+bounds.height*.65);}
   }
   await page.waitForTimeout(70);
  }
  await page.mouse.up();
  const end=await page.evaluate(()=>window.__nightActionTest.snapshot());
  const result={strategy,maxScore,deathStep,collisions,moving:maxMoving,fragile:seenFragile,over:end.over};
  results.push(result);
  await page.screenshot({path:path.join(dir,'action-jump-longplay-'+strategy+'.jpg'),type:'jpeg',quality:80});
  await page.close();
 }
 assert.deepEqual(errors,[],'long-play should never throw a browser exception');
 console.log('SKY_JUMPER_REAL_POINTER_PLAY='+JSON.stringify(results));
 assert.ok(results.every(x=>x.moving>0&&x.fragile),'early hazards must appear on each attempt');
 assert.ok(results.some(x=>x.deathStep>=0),'at least one real test player should lose during an early run');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve))}
