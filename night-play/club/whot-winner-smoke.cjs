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
 const paths={'/audio/sfx.js':'audio/sfx.js','/audio/sfx.css':'audio/sfx.css','/club/':'club/index.html','/club':'club/index.html','/club/style.css':'club/style.css','/game-shell.css':'game-shell.css','/club/ludo-immersive.css':'club/ludo-immersive.css','/club/games.js':'club/games.js','/club/naija-ludo.js':'club/naija-ludo.js','/club/ludo-motion.js':'club/ludo-motion.js','/club/ludo-dice-3d.js':'club/ludo-dice-3d.js'};
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
   assert.ok(look.bg.includes('data:image/svg+xml'),'tabletop uses original NIGHT vector woodgrain');
   assert.ok(look.shadow.includes('inset'),'NIGHT-framed wooden table has subtle depth and separation');
   assert.equal(await page.locator('.whot-opponent-hand .card-back').count(),3,'CPU card backs are deliberately capped at three');
   const compact=await page.evaluate(()=>{const row=document.querySelector('.whot-opponent-hand');const cards=[...row.querySelectorAll('.card-back')];const a=row.getBoundingClientRect(),c=cards.map(el=>el.getBoundingClientRect());return {width:a.width,inside:c.every(r=>r.left>=a.left-1&&r.right<=a.right+1),overlap:c[1]?.left<c[0]?.right&&c[2]?.left<c[1]?.right,scrollWidth:row.scrollWidth,clientWidth:row.clientWidth}});
   assert.equal(compact.inside,true,'CPU card stack stays entirely within its row');
   assert.equal(compact.overlap,true,'opponent cards form a compact physical stack');
   assert.ok(compact.width<(width<=600?205:245),'opponent backs never span the whole felt');
   assert.ok(compact.scrollWidth<=compact.clientWidth+1,'CPU stack never creates horizontal scroll');
   const labels=await page.locator('.whot-opponent-hand .card-back').evaluateAll(cards=>
     cards.map(c=>[...c.querySelectorAll('span')].some(x=>getComputedStyle(x).visibility==='visible')));
   assert.deepEqual(labels,[false,false,true],'only the visible front CPU back has complete WHOT lettering');
   const geometry=await page.evaluate(()=>{
     const rect=s=>{const e=document.querySelector(s),r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}};
     return {cpu:rect('.whot-opponent-hand'),market:rect('.whot-deck-back'),discard:rect('.card-pile>.whot-card'),hand:rect('.hand .card-row'),
       cpuWidth:parseFloat(getComputedStyle(document.querySelector('.whot-opponent-hand .card-back')).width)};
   });
   const structure=await page.evaluate(()=>{
     const rect=selector=>{const el=document.querySelector(selector),r=el.getBoundingClientRect();return {x:r.left,y:r.top,w:r.width,h:r.height,bottom:r.bottom}};
     return {siteHeader:rect('.top'),heading:rect('.play-heading'),table:rect('.table-surface'),hud:rect('.whot-scoreboard'),
       visual:{body:getComputedStyle(document.body).backgroundColor,table:getComputedStyle(document.querySelector('.table-surface')).borderRadius,header:getComputedStyle(document.querySelector('.top')).backgroundColor}};
   });
   console.log('WHOT_PRELAYOUT_DEBUG',width,JSON.stringify({geometry,structure}));
   const h=width===390?105:138;
   assert.equal(geometry.cpuWidth,width===390?80:100,'keep traditional reference card dimensions');
   assert.ok(Math.abs(geometry.market.h-h)<1.5,'market card keeps the original full height');
   assert.ok(Math.abs(geometry.market.y-geometry.discard.y)<2,'market and played card remain aligned');
   assert.ok(geometry.market.x<geometry.discard.x,'market stays left of played cards');
   assert.ok(geometry.market.y-geometry.cpu.y-(width===390?105:138)>(width===390?45:30),'market gets its own breathing room below CPU cards');
   assert.ok(geometry.hand.y-geometry.market.y-h>(width===390?45:30),'player cards sit clearly below the market');
   assert.ok(structure.hud.y-geometry.hand.y-h>10,'turn indicator follows the player hand');
   assert.ok(structure.table.bottom-structure.hud.bottom>=8&&structure.table.bottom-structure.hud.bottom<45,
     'turn indicator is inside the felt frame, never isolated in an empty bottom section');
   assert.ok(structure.table.y>structure.siteHeader.bottom+30,'show game heading and visible NIGHT website frame');
   assert.ok(structure.table.w<width-10,'wood is bounded by website margins, not fullscreen');
   assert.ok(structure.table.h>=600&&structure.table.h<=855,'framed tabletop scales to the viewport');
   assert.ok(structure.visual.table!=='0px','wood table must have rounded card-game edges');
   assert.ok(geometry.hand.y-geometry.cpu.y>(width===390?300:350),'vertical spacing spans the viewport rather than being packed at the top');
   console.log('WHOT_GEOMETRY',width,JSON.stringify({geometry,structure}));
   assert.equal(await page.locator('.whot-win-layer').count(),0,'no premature victory banner');
   assert.equal(await page.locator('.whot-deck-word').count(),2,'classic WHOT back contains exactly two readable labels');
   const deckLetters=await page.locator('.whot-deck-word').evaluateAll(els=>
     els.map(e=>({text:e.textContent,trans:getComputedStyle(e).transform})));
   assert.deepEqual(deckLetters.map(x=>x.text),['WHOT','WHOT'],'no duplicate overlapping pseudo-lettering');
   assert.notEqual(deckLetters[0].trans,'none','upper lettering is inverted on the physical back');
   assert.equal(deckLetters[1].trans,'none','lower lettering stays upright');

   // Match the high-quality classical WHOT face proportions we studied,
   // with NIGHT's original SVG art and without loading the other game.
   const design=await page.locator('.hand .whot-card:not(.whot-wild)').first().evaluate(el=>{
     const r=el.getBoundingClientRect(),cs=getComputedStyle(el);
     const face=el.querySelector('.whot-suit-big'),small=el.querySelector('.whot-suit-small');
     const rank=el.querySelector('.whot-corner strong');
     return {width:parseFloat(cs.width),height:parseFloat(cs.height),animatedWidth:r.width,animatedHeight:r.height,bg:cs.backgroundColor,ink:getComputedStyle(face||el).color,
       symbolFill:face?getComputedStyle(face).fill:null,
       symbolWidth:face?.getBoundingClientRect().width||0,
       cornerSize:rank?getComputedStyle(rank).fontSize:null,
       cornerInk:rank?getComputedStyle(rank).color:null,
       hasSmallSuit:!!small,
       cssGradient:cs.backgroundImage};
   });

   console.log('WHOT_CARD_DESIGN',width,JSON.stringify(design));
   assert.equal(design.bg,'rgb(255, 254, 251)','original, clean ivory playing-card stock');
   assert.equal(design.cssGradient,'none','no glossy artificial paper gradient');
   assert.equal(design.symbolFill,'rgb(84, 26, 39)','solid burgundy geometric suits across all contexts');
   assert.equal(design.cornerInk,'rgb(27, 32, 34)','small charcoal indices rather than decorative large ranks');
   assert.equal(design.hasSmallSuit,true,'numbers have accompanying corner symbols');
   assert.ok(design.symbolWidth>25,'single central geometric suit is unmistakable');
   if(width===390){
     assert.ok(Math.abs(design.width-80)<1,'reference 80px mobile face');
     assert.ok(Math.abs(design.height-105)<1,'reference 105px mobile face');
     assert.equal(design.cornerSize,'10px');
   }else{
     assert.ok(Math.abs(design.width-100)<1,'reference-proportioned desktop card width');
     assert.ok(Math.abs(design.height-138)<1,'reference-proportioned desktop card height');
     assert.equal(design.cornerSize,'12px');
   }
   console.log('WHOT_CARD_DESIGN',width,JSON.stringify(design));

   const budget=await page.evaluate(()=>window.__nightWhotMotion().timings);
   assert.ok(budget.player>=155&&budget.player<=235&&budget.cpu>=170&&budget.cpu<=250&&budget.draw>=125&&budget.draw<=195,'fast tap-driven movement must not stall on slow 500ms+ flights');
   assert.ok(budget.cpuDelay>=40&&budget.cpuDelay<=125,'CPU starts promptly instead of pausing a quarter-second');
   assert.ok(budget.deal<=250&&budget.dealStagger<=40,'initial deal finishes quickly');
   assert.ok(budget.settle<=120&&budget.recenter<=170,'no lengthy follow-on card settle or recenter');
   const dealAnim=await page.locator('.hand .whot-card').last().evaluate(el=>{
     const active=el.getAnimations();return active.length?active[0].effect.getTiming():null
   });
   if(dealAnim){
     assert.ok(dealAnim.duration<=250,'actual dealt-card animation must stay under 250ms');
     assert.ok(dealAnim.delay<=160,'5-card dealing must be fast and rhythmic');
   }
   const liveSpeed=await page.evaluate(()=>{
     const table=document.querySelector('.table-surface .whot-win-layer');
     return {winner:!!table};
   });
   assert.equal(liveSpeed.winner,false,'no result animation during live gameplay');
   await page.waitForTimeout(1400);
   assert.equal(await page.locator('.hand .whot-card').count(),5,'all five dealt cards remain visible in the hand');
   const upright=await page.locator('.hand .whot-card').evaluateAll(nodes=>nodes.every(el=>{
     const m=getComputedStyle(el).transform;
     const r=el.getBoundingClientRect();
     return (m==='none'||m==='matrix(1, 0, 0, 1, 0, 0)')&&Math.abs(r.width-parseFloat(getComputedStyle(el).width))<.8;
   }));
   assert.equal(upright,true,'hand cards remain straight and readable instead of an uneven fan');
   const visible=await page.locator('.hand .whot-card').evaluateAll(nodes=>nodes.every(el=>{
     const parent=el.closest('.card-row').getBoundingClientRect(),r=el.getBoundingClientRect();
     return r.left>=parent.left-.8&&r.right<=parent.right+1;
   }));
   assert.equal(visible,true,'the entire five-card starting hand fits without horizontal scrolling');
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
   // CPU can accumulate a huge hand after chained penalties. Its compact
   // visual stays three backs while the existing HUD remains truthful.
   const fatCpu=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   await fatCpu.goto(base+'/club/?game=whot');
   await fatCpu.evaluate(()=>{
     const key='night-play-club-v1',obj=JSON.parse(localStorage.getItem(key)),s=obj.whot;
     s.hands[1].push(...s.deck.splice(0,Math.min(15,s.deck.length)));
     localStorage.setItem(key,JSON.stringify(obj));
   });
   await fatCpu.reload();
   await fatCpu.locator('.whot-seat-cpu strong').waitFor();
   const tall=await fatCpu.evaluate(()=>{
     const cpu=JSON.parse(localStorage.getItem('night-play-club-v1')).whot.hands[1].length;
     const backs=[...document.querySelectorAll('.whot-opponent-hand .card-back')];
     const zone=document.querySelector('.whot-opponent-hand').getBoundingClientRect();
     const last=backs.at(-1).getBoundingClientRect();
     return {cpu,backs:backs.length,shown:Number(document.querySelector('.whot-seat-cpu strong').textContent),
       visibleInside:last.right<=zone.right+1,scroll:document.querySelector('.whot-opponent-hand').scrollWidth};
   });
   assert.ok(tall.cpu>=15,'fixture actually adds cards to the CPU hand');
   assert.equal(tall.shown,tall.cpu,'HUD shows the REAL number of cards');
   assert.equal(tall.backs,3,'large opponent hands never render more than three decorative card backs');
   assert.equal(tall.visibleInside,true,'stack stays on the board with fifteen-plus CPU cards');
   await fatCpu.close();
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
   const drawnFace=await drawPage.evaluate(()=>{
     const e=document.querySelector('.whot-flip-front .whot-flight-face');
     const dest=[...document.querySelectorAll('.hand .whot-card')].at(-1);
     if(!e||!dest)return null;
     const print=x=>({paper:getComputedStyle(x).backgroundColor,
       ink:getComputedStyle(x.querySelector('.whot-suit-big')||x).fill,
       rank:getComputedStyle(x.querySelector('.whot-corner strong')).fontSize});
     return {key:e.dataset.whotKey,destKey:dest.dataset.whotKey,
       print:print(e),destPrint:print(dest)};
   });
   assert.ok(drawnFace,'drawn face exists during flip');
   assert.equal(drawnFace.key,drawnFace.destKey,'in-flight draw shows the real incoming card, not a placeholder');
   assert.deepEqual(drawnFace.print,drawnFace.destPrint,'draw flip uses the same finalized art as the destination card');
   const flipStyle=await drawPage.locator('.whot-flip-core').evaluate(el=>({
     effect:el.getAnimations().map(a=>a.effect.getTiming().duration),
     transformStyle:getComputedStyle(el).transformStyle,
     backface:getComputedStyle(el.querySelector('.whot-flip-front')).backfaceVisibility
   }));
   assert.ok(flipStyle.effect.includes(150),'fast 3D card reveal ends within the flight');
   assert.equal(flipStyle.transformStyle,'preserve-3d');
   assert.equal(flipStyle.backface,'hidden','reverse side must stay hidden while rotating');
      const motion=await drawPage.evaluate(()=>window.__nightWhotMotion());
   assert.equal(motion.lastFlightAction,'draw','market draw uses true flight animation');
   assert.equal(motion.lastFlightDuration,150,'market draw takes 150ms, not half a second');
   await drawPage.waitForFunction(()=>window.__nightWhotMotion().activeFlights===0,null,{timeout:1800});
   assert.notEqual(await newFace.evaluate(el=>el.style.visibility),'hidden','new face reveals when the drawn back reaches its real position');
   assert.equal(await drawPage.locator('.hand .whot-card').count(),6,'no extra second-card popup');
   assert.deepEqual(drawErrors,[],'market draw has no JavaScript exceptions');
   await drawPage.close();
   // DOM continuity is the performance fix: do not rebuild the entire felt
   // board or replace cards that are still in the player's hand.
   const carry=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   const carryErrors=[];carry.on('pageerror',e=>carryErrors.push(e.message));
   await carry.route('https://cdn.jsdelivr.net/**',route=>route.abort());
   await carry.goto(base+'/club/?game=whot');
   await carry.evaluate(()=>{
     const key='night-play-club-v1',obj=JSON.parse(localStorage.getItem(key)),w=obj.whot;
     w.hands[0][0]={s:0,n:3};w.discard[w.discard.length-1]={s:0,n:3}; // not a 20 picker
     w.turn=0;w.winner=null;w.pendingWhot=null;w.called=null;
     localStorage.setItem(key,JSON.stringify(obj));
   });
   await carry.reload();
   await carry.locator('.hand .whot-card.playable').first().waitFor();
   await carry.evaluate(()=>{
     window.__tableNode=document.querySelector('.table-surface');
     window.__deckNode=document.querySelector('.whot-deck');
     window.__opponentNode=document.querySelector('.table-surface>.card-row .card-back');
     window.__flightParityOrigin=document.querySelector('.hand .whot-card[data-card="0"]');
     window.__remainingNode=document.querySelectorAll('.hand .whot-card')[1];
     window.__cardCountBefore=document.querySelectorAll('.hand .whot-card').length;
   });
   await carry.locator('.hand .whot-card[data-card="0"]').click();
   await carry.waitForFunction(()=>window.__nightWhotMotion().activeFlights===1,null,{timeout:1200});
   const parity=await carry.evaluate(()=>{
     const a=window.__flightParityOrigin,b=document.querySelector('.whot-flight-face');
     if(!a||!b)return null;
     const details=el=>{
       const c=getComputedStyle(el),r=getComputedStyle(el.querySelector('.whot-corner strong'));
       const face=getComputedStyle(el.querySelector('.whot-suit-big'));
       return {background:c.backgroundColor,gradient:c.backgroundImage,ink:c.color,
         rank:r.color,rankSize:r.fontSize,shapeFill:face.fill,
         shapeWidth:getComputedStyle(el.querySelector('.whot-suit-big')).width};
     };
     return {source:details(a),flight:details(b)};
   });
   assert.ok(parity,'flight sprite exists during play, not only after it ends');
   assert.deepEqual(parity.flight,parity.source,'the flight must never flash an obsolete card face, including ranks and symbols');
   await snap(carry,'FLIGHT',width);
   await carry.waitForFunction(()=>document.querySelectorAll('.hand .whot-card').length===window.__cardCountBefore-1,null,{timeout:2000});
   const continuity=await carry.evaluate(()=>({
     table:document.querySelector('.table-surface')===window.__tableNode,
     market:document.querySelector('.whot-deck')===window.__deckNode,
     opponent:document.querySelector('.table-surface>.card-row .card-back')===window.__opponentNode,
     remaining:[...document.querySelectorAll('.hand .whot-card')].includes(window.__remainingNode),
     cardCount:document.querySelectorAll('.hand .whot-card').length,
     fancyBlur:getComputedStyle(document.querySelector('.whot-scoreboard')).backdropFilter
   }));
   assert.equal(continuity.table,true,'felt table remains mounted on every card move');
   assert.equal(continuity.market,true,'market pile does not re-render on play');
   assert.equal(continuity.opponent,true,'opponent deck stays mounted on play');
   assert.equal(continuity.remaining,true,'recentered hand cards retain DOM identity');
   assert.equal(continuity.cardCount,4,'playing a card takes exactly one card from the hand');
   assert.equal(continuity.fancyBlur,'none','WHOT HUD avoids GPU-heavy live backdrop filtering');
   assert.deepEqual(carryErrors,[],'incremental WHOT board update has no JS errors');
   await carry.close();
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
   const cpuCardPrint=await cpuPage.locator('.whot-flip-front .whot-flight-face').evaluate(el=>({
     paper:getComputedStyle(el).backgroundColor,
     suit:getComputedStyle(el.querySelector('.whot-suit-big')||el).fill,
     rank:getComputedStyle(el.querySelector('.whot-corner strong')).fontSize
   }));
   assert.equal(cpuCardPrint.paper,'rgb(255, 254, 251)','CPU flip never exposes the old card stock');
   assert.equal(cpuCardPrint.suit,'rgb(84, 26, 39)','CPU flip uses the new deep-wine suit ink');
   assert.equal(cpuCardPrint.rank,width===390?'10px':'12px','CPU flip keeps the same small rank size');
   await cpuPage.waitForFunction(()=>window.__nightWhotMotion().activeFlights===0,null,{timeout:1500});
   assert.equal(await cpuPage.locator('.whot-flight').count(),0,'CPU flight element is cleaned up');
   assert.deepEqual(cpuErrors,[],'CPU 3D animation has no script errors');
   await cpuPage.close();
   // Regression: CPU Pick Three must deal exactly THREE cards to the
   // player's own animated slots. The old implementation showed all 3
   // instantly and then animated just one Market card.
   const penaltyPage=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   const penaltyErrors=[];penaltyPage.on('pageerror',e=>penaltyErrors.push(e.message));
   await penaltyPage.route('https://cdn.jsdelivr.net/**',route=>route.abort());
   await penaltyPage.goto(base+'/club/?game=whot');
   await penaltyPage.evaluate(()=>{
     const key='night-play-club-v1',data=JSON.parse(localStorage.getItem(key)),s=data.whot;
     s.hands[0]=s.hands[0].map((c,i)=>i===0?{s:0,n:3}:c.n===5?{s:0,n:7}:c);
     s.hands[1][0]={s:0,n:5};
     s.discard[s.discard.length-1]={s:0,n:3};
     s.turn=0;s.winner=null;s.pendingPenalty=null;s.pendingFinish=null;
     s.pendingWhot=null;s.called=null;
     localStorage.setItem(key,JSON.stringify(data));
   });
   await penaltyPage.reload();
   await penaltyPage.locator('.hand [data-card="0"]').click();
   await penaltyPage.waitForFunction(()=>window.__nightWhotMotion().lastFlightAction==='cpu'
     &&window.__nightWhotMotion().activeFlights===1,null,{timeout:4800});
   const cpuAnimation=await penaltyPage.evaluate(()=>{
     const e=document.querySelector('.whot-flip-back .card-back');
     const face=document.querySelector('.whot-flip-front .whot-card');
     return {faces:document.querySelectorAll('.whot-flight-flip').length,
       backWords:e?.querySelectorAll('span').length,
       extraLabel:e?getComputedStyle(e,'::after').content:null,
       front:face?.querySelector('.whot-suit-big')!=null};
   });
   assert.equal(cpuAnimation.faces,1,'CPU actually throws one animated card');
   assert.equal(cpuAnimation.backWords,2,'CPU travelling back has both authentic WHOT words');
   assert.equal(cpuAnimation.extraLabel,'none','no obsolete third WHOT word on moving back');
   assert.equal(cpuAnimation.front,true,'CPU turn reveals the real card face');
   await snap(penaltyPage,'CPU-FLIP',width);
   await penaltyPage.waitForFunction(()=>window.__nightWhotMotion().lastFlightAction==='draw'
      &&window.__nightWhotMotion().activeFlights>=1,null,{timeout:3800});
   const staged=await penaltyPage.locator('.hand .whot-card').evaluateAll(cards=>({
     count:cards.length,hidden:cards.filter(c=>getComputedStyle(c).visibility==='hidden').length
   }));
   assert.equal(staged.count,7,'after CPU Pick Three, the player receives three real cards');
   assert.ok(staged.hidden>=1,'new penalty cards remain hidden until their own flight finishes');
   await snap(penaltyPage,'PICK-THREE',width);
   await penaltyPage.waitForFunction(()=>window.__nightWhotMotion().activeFlights===0
      &&window.__nightWhotMotion().draws>=3,null,{timeout:3500});
   assert.equal(await penaltyPage.locator('.hand .whot-card').count(),7);
   assert.equal(await penaltyPage.locator('.hand .whot-card').evaluateAll(cards=>cards.filter(c=>getComputedStyle(c).visibility==='hidden').length),0,
     'all penalty cards reveal only after their arrival');
   assert.deepEqual(penaltyErrors,[],'CPU penalty flights have no browser JS errors');
   await penaltyPage.close();
   // Defence must remain a player's decision. Do not automatically draw
   // when a matching 5 is available to return the Pick Three.
   const defendPage=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   await defendPage.goto(base+'/club/?game=whot');
   await defendPage.evaluate(()=>{
     const key='night-play-club-v1',data=JSON.parse(localStorage.getItem(key)),s=data.whot;
     s.hands[0]=s.hands[0].map((c,i)=>i===0?{s:0,n:3}:i===1?{s:2,n:5}:c.n===5?{s:0,n:7}:c);
     s.hands[1][0]={s:0,n:5};s.discard[s.discard.length-1]={s:0,n:3};
     s.turn=0;s.winner=null;s.pendingPenalty=null;s.pendingFinish=null;
     s.pendingWhot=null;s.called=null;
     localStorage.setItem(key,JSON.stringify(data));
   });
   await defendPage.reload();
   await defendPage.locator('.hand [data-card="0"]').click();
   await defendPage.waitForFunction(()=>document.querySelector('.whot-turn-indicator')?.textContent.includes('DEFEND / PICK 3'),
     null,{timeout:4500});
   await defendPage.waitForTimeout(340);
   assert.equal(await defendPage.locator('.hand .whot-card').count(),4,'do not force penalty draw when 5 defence exists');
   assert.equal(await defendPage.locator('.hand .whot-card.playable').count(),1,'only the matching 5 can defend');
   await defendPage.close();
  }
  console.log('WHOT WINNER QA PASS: immersive table, one HUD, player/CPU result overlays, rematch, no redundant information, mobile/desktop');
 }finally{await browser.close()}
})().catch(e=>{console.error('WHOT WINNER QA FAILED',e.stack||e);process.exitCode=1}).finally(()=>server.close());