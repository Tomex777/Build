'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve(__dirname,'..');
const fileTypes={'.css':'text/css','.js':'application/javascript','.html':'text/html'};
const server=http.createServer((req,res)=>{
 const src=new URL(req.url,'http://localhost').pathname;
 const paths={'/club/':'club/index.html','/club':'club/index.html','/club/style.css':'club/style.css','/game-shell.css':'game-shell.css','/club/ludo-immersive.css':'club/ludo-immersive.css','/club/games.js':'club/games.js','/club/naija-ludo.js':'club/naija-ludo.js','/club/ludo-motion.js':'club/ludo-motion.js','/club/ludo-dice-3d.js':'club/ludo-dice-3d.js'};
 const rel=paths[src];
 if(!rel){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':fileTypes[path.extname(rel)]||'text/plain','cache-control':'no-store'});
 res.end(fs.readFileSync(path.join(root,rel)));
});
function snap(page,tag,width){
 return page.screenshot({type:'jpeg',quality:58,fullPage:true}).then(buffer=>{
  if(process.env.WHOT_QA_IMAGE==='1'&&width===390)console.log('WHOT_QA_IMAGE_'+tag+'='+buffer.toString('base64'));
 });
}
async function finish(page,winner){
 await page.evaluate(winner=>{
  const key='night-play-club-v1';
  const obj=JSON.parse(localStorage.getItem(key));
  const whot=obj.whot;
  whot.discard.push(...whot.hands[winner]);
  whot.hands[winner]=[];
  whot.winner=winner;whot.turn=winner;whot.pendingWhot=null;
  whot.msg=winner===0?'You win!':'Computer wins.';
  localStorage.setItem(key,JSON.stringify(obj));
 },winner);
 await page.reload();
 await page.locator('.whot-win-layer').waitFor({state:'visible'});
}
(async()=>{
 await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
 const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  for(const width of [390,1280]){
   const page=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.route('https://cdn.jsdelivr.net/**',route=>route.abort());
   await page.goto(base+'/club/?game=whot');
   await page.locator('.whot-scoreboard').waitFor();
   assert.equal(await page.locator('.whot-scoreboard').count(),1,'one board scoreboard');
   assert.equal(await page.locator('.controls-col').isVisible(),false,'remove redundant sidebar');
   assert.equal(await page.locator('#gameSubtitle').isVisible(),false,'remove decorative subtitle');
   assert.equal(await page.locator('#gameCategory').isVisible(),false,'remove category label');
   assert.equal(await page.locator('.whot-seat-you strong').innerText(),'5');
   assert.equal(await page.locator('.whot-seat-cpu strong').innerText(),'5');
   assert.match(await page.locator('.whot-turn-indicator').innerText(),/YOUR TURN/);
   assert.equal(await page.locator('.hand .card-label').count(),0,'no duplicate hand count');
   assert.equal(await page.locator('.table-surface > .card-row .cards-counter').count(),0,'no duplicate CPU count');
   const look=await page.locator('.table-surface').evaluate(el=>({bg:getComputedStyle(el).backgroundImage,shadow:getComputedStyle(el).boxShadow,radius:getComputedStyle(el).borderRadius}));
   assert.ok(look.bg.includes('repeating-linear-gradient'),'felt material has visible woven texture');
   assert.ok(look.shadow.includes('inset'),'felt table includes raised wooden edge');
   assert.equal(await page.locator('.whot-win-layer').count(),0,'no premature victory banner');
   const budget=await page.evaluate(()=>window.__nightWhotMotion().timings);
   assert.ok(budget.player>=380&&budget.player<=450&&budget.cpu>=400&&budget.cpu<=460&&budget.draw>=380&&budget.draw<=440,'card motion should glide rather than snap or drag');
   assert.ok(budget.cpuDelay>=150&&budget.cpuDelay<=220,'CPU should pause naturally without the old 380ms dead time');
   assert.ok(budget.deal<=450&&budget.dealStagger<=70,'initial dealing should stay rhythmic and smooth');
   assert.ok(budget.settle<=210&&budget.recenter>=220&&budget.recenter<=290,'centered hands should glide naturally into position');
   const dealAnim=await page.locator('.hand .whot-card').last().evaluate(el=>{
     const active=el.getAnimations();return active.length?active[0].effect.getTiming():null
   });
   if(dealAnim){
     assert.ok(dealAnim.duration<=450,'actual dealt-card animation must stay under 450ms');
     assert.ok(dealAnim.delay<=330,'5-card dealing should be evenly staggered');
   }
   const liveSpeed=await page.evaluate(()=>{
     const table=document.querySelector('.table-surface .whot-win-layer');
     return {winner:!!table};
   });
   assert.equal(liveSpeed.winner,false,'no result animation during live gameplay');
   await page.waitForTimeout(1400);
   assert.equal(await page.locator('.hand .whot-card').count(),5,'all five dealt cards remain visible in the hand');
   await snap(page,'TABLE',width);
   await finish(page,0);
   assert.equal(await page.locator('.whot-win-layer.player-victory').count(),1,'winner rendered inside board');
   assert.equal(await page.locator('.whot-win-content h2').innerText(),'YOU WIN');
   assert.equal(await page.locator('.whot-win-count').innerText(),'CPU · 5 CARDS LEFT');
   assert.equal(await page.locator('.night-action-row').isVisible(),false,'no duplicate new game action over victory');
   assert.equal(await page.locator('.whot-win-particles i').count(),18,'small visual victory celebration');
   const bounds=await page.locator('.whot-win-layer').boundingBox();
   const tableBounds=await page.locator('.table-surface').boundingBox();
   const isInTable=await page.locator('.whot-win-layer').evaluate(layer=>layer.parentElement?.matches('.table-surface.whot-finished'));
   assert.ok(isInTable,'result is mounted inside the actual felt table');
   assert.ok(Math.abs(bounds.x-tableBounds.x)<12&&Math.abs(bounds.y-tableBounds.y)<12,
    'result overlays the playing table excluding only its raised wooden border');
   const resultMotion=await page.locator('.whot-win-content').evaluate(el=>getComputedStyle(el).animationDuration);
   assert.equal(resultMotion,'0.4s','winner text should animate promptly');
   await page.waitForTimeout(700);
   await snap(page,'WIN',width);
   await page.getByRole('button',{name:/Play again/}).click();
   assert.equal(await page.locator('.whot-win-layer').count(),0,'rematch removes result and starts a fresh hand');
   assert.equal(await page.locator('.hand .whot-card').count(),5,'rematch deals five fresh cards');
   assert.equal(await page.locator('.whot-seat-cpu strong').innerText(),'5');
   await finish(page,1);
   assert.equal(await page.locator('.whot-win-layer.computer-victory').count(),1,'CPU win is on same board');
   assert.equal(await page.locator('.whot-win-content h2').innerText(),'CPU WINS');
   assert.equal(await page.locator('.whot-win-count').innerText(),'YOU · 5 CARDS LEFT');
   assert.equal(await page.locator('.whot-win-particles i').count(),0,'CPU win is distinguished from a player victory');
   await page.waitForTimeout(620);
   await snap(page,'LOSE',width);
   const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
   assert.ok(overflow<=1,'no horizontal overflow at '+width+'px: '+overflow);
   assert.deepEqual(errors,[],'no console page exceptions');
   await page.close();
   // Independent game context: verify market draw reaches a newly created
   // card slot rather than animating onto the previous final card.
   const drawPage=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   const drawErrors=[];drawPage.on('pageerror',e=>drawErrors.push(e.message));
   await drawPage.route('https://cdn.jsdelivr.net/**',route=>route.abort());
   await drawPage.goto(base+'/club/?game=whot');
   await drawPage.locator('.hand .whot-card').first().waitFor();
   assert.equal(await drawPage.locator('.hand .whot-card').count(),5,'five initial cards before picking');
   await drawPage.locator('.whot-deck').click();
   await drawPage.waitForFunction(()=>window.__nightWhotMotion().activeFlights===1,null,{timeout:1300});
   assert.equal(await drawPage.locator('.hand .whot-card').count(),6,'new card is laid out before the flight');
   const newFace=drawPage.locator('.hand .whot-card').last();
   assert.equal(await newFace.evaluate(el=>el.style.visibility),'hidden','new face stays hidden while market back is flying');
   // The incoming card is a genuine two-sided 3D flip during the same flight.
   assert.equal(await drawPage.locator('.whot-flight-flip .whot-flip-core').count(),1,'draw animates an actual 3D card, not a flat sprite');
   assert.equal(await drawPage.locator('.whot-flip-core .whot-flip-side').count(),2,'card has front and back faces');
   const flipStyle=await drawPage.locator('.whot-flip-core').evaluate(el=>({
     effect:el.getAnimations().map(a=>a.effect.getTiming().duration),
     transformStyle:getComputedStyle(el).transformStyle,
     backface:getComputedStyle(el.querySelector('.whot-flip-front')).backfaceVisibility
   }));
   assert.ok(flipStyle.effect.includes(340),'3D flip shares the source-to-destination flight');
   assert.equal(flipStyle.transformStyle,'preserve-3d');
   assert.equal(flipStyle.backface,'hidden','reverse side must stay hidden while rotating');
      const motion=await drawPage.evaluate(()=>window.__nightWhotMotion());
   assert.equal(motion.lastFlightAction,'draw','market draw uses true flight animation');
   assert.equal(motion.lastFlightDuration,415,'market draw uses smooth 415ms card flight');
   await drawPage.waitForFunction(()=>window.__nightWhotMotion().activeFlights===0,null,{timeout:1800});
   assert.notEqual(await newFace.evaluate(el=>el.style.visibility),'hidden','new face reveals when the drawn back reaches its real position');
   assert.equal(await drawPage.locator('.hand .whot-card').count(),6,'no extra second-card popup');
   assert.deepEqual(drawErrors,[],'market draw has no JavaScript exceptions');
   await drawPage.close();
   // Match the reference's physical reveal: CPU throws a card face-down,
   // rotating it over the flight while its new face comes into view.
   const cpuPage=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   const cpuErrors=[];cpuPage.on('pageerror',e=>cpuErrors.push(e.message));
   await cpuPage.route('https://cdn.jsdelivr.net/**',route=>route.abort());
   await cpuPage.goto(base+'/club/?game=whot');
   await cpuPage.evaluate(()=>{
     const key='night-play-club-v1',data=JSON.parse(localStorage.getItem(key)),s=data.whot;
     const regular={s:0,n:3};
     s.discard[s.discard.length-1]={...regular};
     s.hands[0][0]={...regular};s.hands[1][0]={...regular};
     s.turn=0;s.winner=null;s.pendingWhot=null;s.called=null;
     localStorage.setItem(key,JSON.stringify(data));
   });
   await cpuPage.reload();
   await cpuPage.locator('.hand .whot-card[data-card="0"]').click();
   await cpuPage.waitForFunction(()=>window.__nightWhotMotion().lastFlightAction==='cpu'&&window.__nightWhotMotion().activeFlights===1,null,{timeout:3500});
   assert.equal(await cpuPage.locator('.whot-flight-flip .whot-flip-core').count(),1,'CPU uses the 3D turning-card animation');
   assert.ok(await cpuPage.locator('.whot-flip-front .whot-card').count()===1,'CPU reveals the correct card face mid-flight');
   await cpuPage.waitForFunction(()=>window.__nightWhotMotion().activeFlights===0,null,{timeout:1500});
   assert.equal(await cpuPage.locator('.whot-flight').count(),0,'CPU flight element is cleaned up');
   assert.deepEqual(cpuErrors,[],'CPU 3D animation has no script errors');
   await cpuPage.close();
  }
  console.log('WHOT WINNER QA PASS: immersive table, one HUD, player/CPU result overlays, rematch, no redundant information, mobile/desktop');
 }finally{await browser.close()}
})().catch(e=>{console.error('WHOT WINNER QA FAILED',e.stack||e);process.exitCode=1}).finally(()=>server.close());