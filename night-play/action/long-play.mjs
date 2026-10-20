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
  let maxScore=0,maxMoving=0,seenVanishing=false,collisions=0,lastVy=0,deathStep=-1,aim=null;
  await page.mouse.move(bounds.x+bounds.width*.46,bounds.y+bounds.height*.65);
  await page.mouse.down();
  for(let i=0;i<steps;i++){
   const s=await page.evaluate(()=>window.__nightActionTest.snapshot());
   maxScore=Math.max(maxScore,s.score);maxMoving=Math.max(maxMoving,s.platforms.filter(p=>p.moving).length);
   if(s.platforms.some(p=>p.fragile||p.ttl!==undefined))seenVanishing=true;
   if(lastVy>0&&s.player.vy<0){collisions++;aim=null}
   lastVy=s.player.vy;
   if(s.over){deathStep=i;break}
   if(strategy==='steer'){
    const p=s.player,foot=p.y+17;
    if(aim&&!s.platforms.some(q=>q.baseX===aim.baseX&&q.w===aim.w))aim=null;
    if(!aim){
      // A skilled Doodle Jump player aims UPWARD while rising, then lands
      // while falling. Find platforms inside the real ballistic jump arc,
      // reject unreachable horizontal moves and prefer real height gain.
      const apexFoot=foot-Math.min(0,p.vy)**2/(2*1230);
      const choices=s.platforms.filter(q=>q.y>=apexFoot-4&&q.y<foot-24)
        .filter(q=>{
          const d=Math.abs(q.x+q.w*.5-p.x),wrapped=Math.min(d,390-d);
          const discriminant=p.vy*p.vy+2*1230*(q.y-foot);
          if(discriminant<0)return false;
          const landingTime=(-p.vy+Math.sqrt(discriminant))/1230;
          return wrapped<Math.max(50,landingTime*285)+q.w*.3;
        }).sort((a,b)=>a.y-b.y);
      const selected=choices[0];
      if(selected)aim={baseX:selected.baseX,w:selected.w};
    }
    const chosen=aim&&s.platforms.find(q=>q.baseX===aim.baseX&&q.w===aim.w);
    if(chosen){
      const x=Math.min(384,Math.max(6,chosen.x+chosen.w/2));
      await page.mouse.move(bounds.x+x/390*bounds.width,bounds.y+bounds.height*.65);
    }
   }
   await page.waitForTimeout(70);
  }
  await page.mouse.up();
  const end=await page.evaluate(()=>window.__nightActionTest.snapshot());
  const result={strategy,maxScore,deathStep,collisions,moving:maxMoving,vanishing:seenVanishing,over:end.over};
  results.push(result);
  await page.screenshot({path:path.join(dir,'action-jump-longplay-'+strategy+'.jpg'),type:'jpeg',quality:80});
  await page.close();
 }
 assert.deepEqual(errors,[],'long-play should never throw a browser exception');
 console.log('SKY_JUMPER_REAL_POINTER_PLAY='+JSON.stringify(results));
 assert.ok(results.every(x=>x.moving>0&&!x.vanishing),'moving platforms can challenge without disappearing or breaking');
 assert.ok(results.some(x=>x.collisions>=1),'actual play must cause a bounce off a landing platform');
 assert.ok(results.find(x=>x.strategy==='steer').maxScore>=80,'skilled touch-guided play must climb at least 80px, not just bounce forever at zero height');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve))}

// The preview CI already runs this file; execute Agent 3's independent
// browser collision, touchscreen and viewport suite in the same gate.
await import('./agent3-play.mjs');
