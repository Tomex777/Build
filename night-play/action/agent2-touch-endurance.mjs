// Agent 2 NIGHT Play: 3-minute REAL Chromium touch endurance on four phone widths.
// Exercises a running game, not a synthetic canvas or simulated score injection.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/action/':'action/index.html','/action/style.css':'action/style.css',
 '/action/games.js':'action/games.js','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const key=new URL(req.url,'http://127.0.0.1').pathname,file=routes[key];
 if(!file){res.writeHead(404);res.end('No route');return;}
 res.writeHead(200,{'content-type':file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:320,height:720},hasTouch:true,isMobile:true});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
 await page.goto('http://127.0.0.1:'+server.address().port+'/action/?game=slice');
 await page.locator('#overlayStart').click();
 const cdp=await page.context().newCDPSession(page);
 const sizes=[{w:320,h:700},{w:360,h:740},{w:390,h:844},{w:412,h:915}];
 const duration=180000,interval=duration/sizes.length,start=Date.now();
 let touches=0,successful=0,restarts=0,deaths=0,comboEvents=0,maxRoundScore=0,id=250,firstCut=false;
 let lastSize=-1,lastFrame=-1,frameProgress=0,skippedBombs=0;
 const captures=[],samples=[];
 const snapshot=()=>page.evaluate(()=>window.__nightActionTest.snapshot());
 while(Date.now()-start<duration){
  const elapsed=Date.now()-start;
  const i=Math.min(sizes.length-1,Math.floor(elapsed/interval));
  if(i!==lastSize){
   const s=sizes[i];await page.setViewportSize({width:s.w,height:s.h});
   const m=await page.locator('canvas').evaluate(c=>{
    const b=c.getBoundingClientRect();return {right:b.right,left:b.left,width:b.width,scroll:document.documentElement.scrollWidth,viewport:innerWidth};
   });
   assert.ok(m.right<=s.w+1&&m.left>=-1&&m.scroll<=s.w+2,'no Fruit Slice mobile clipping: '+JSON.stringify({s,m}));
   const file='action-agent2-endurance-'+s.w+'.jpg';
   await page.screenshot({path:path.join(output,file),type:'jpeg',quality:77});
   captures.push(file);lastSize=i;
  }
  const state=await snapshot();
  if(state.over){
   deaths++;maxRoundScore=Math.max(maxRoundScore,state.score);
   await page.locator('#overlayStart').click();restarts++;lastFrame=-1;
   continue;
  }
  if(!state.started){
   await page.locator('#overlayStart').click();
   continue;
  }
  if(lastFrame>=0&&state.frame>=lastFrame)frameProgress+=state.frame-lastFrame;
  lastFrame=state.frame;
  maxRoundScore=Math.max(maxRoundScore,state.score);
  if(state.popups.some(p=>typeof p.label==='string'&&p.label.includes('COMBO')))comboEvents++;
  const target=state.fruits
   .filter(f=>f.kind!=='bomb'&&!f.hit&&Math.abs(f.vy)<135&&f.y>127&&f.y<300&&f.x>46&&f.x<344)
   .filter(f=>!state.fruits.some(b=>b.kind==='bomb'&&!b.hit&&Math.hypot(f.x-b.x,f.y-b.y)<84))
   .sort((a,b)=>Math.abs(a.vy)-Math.abs(b.vy))[0];
  if(target){
   const box=await page.locator('canvas').boundingBox();
   const coord=(x,y)=>({x:box.x+x/390*box.width,y:box.y+y/550*box.height,id});
   const oldScore=state.score;
   await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[coord(target.x-41,target.y)]});
   for(let n=1;n<=5;n++){
    await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',
     touchPoints:[coord(target.x-41+n*82/5,target.y)]});
   }
   await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   id++;touches++;
   const after=await snapshot();
   if(after.score>oldScore){
    successful++;
    if(!firstCut){
     const file='action-agent2-endurance-real-cut.jpg';
     await page.screenshot({path:path.join(output,file),type:'jpeg',quality:80});
     captures.push(file);firstCut=true;
    }
   }
  }else if(state.fruits.some(f=>f.kind==='bomb'))skippedBombs++;
  if(samples.length<1000&&Math.floor(elapsed/1000)%10===0&&(!samples.length||elapsed-samples.at(-1).t>8500))
   samples.push({t:elapsed,width:sizes[i].w,frame:state.frame,score:state.score,misses:state.misses,living:!state.over,fruits:state.fruits.length});
  await page.waitForTimeout(target?58:70);
 }
 const final=await snapshot();
 assert.equal(errors.length,0,'no browser errors after 3 minutes: '+errors.slice(0,3));
 assert.ok(touches>=12,'real touch test must actually drive slices throughout the long session: '+touches);
 assert.ok(successful>=5,'five or more TOUCH-REGISTERED cuts must score without duplicate swipes: '+JSON.stringify({touches,successful}));
 assert.ok(frameProgress>=1300,'animation must progress for 3 minutes: '+frameProgress);
 assert.ok(captures.filter(f=>/endurance-\\d+/.test(f)).length===4,'visual snapshots at four mobile widths');
 const summary={durationSeconds:Math.round((Date.now()-start)/1000),sizes,touches,successful,
  firstAttemptRate:Number((successful/Math.max(1,touches)).toFixed(3)),
  deaths,restarts,maxRoundScore,comboEvents,skippedBombs,frameProgress,
  final:{score:final.score,misses:final.misses,started:final.started,over:final.over},
  captures,samples,errors,environment:{chromium:browser.version()}};
 writeFileSync(path.join(output,'action-agent2-endurance-results.json'),JSON.stringify(summary,null,2));
 console.log('NIGHT_A2_3MIN_TOUCH_ENDURANCE='+JSON.stringify(summary));
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
