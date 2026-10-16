import assert from "node:assert/strict";
import {mkdirSync} from "node:fs";
import {createServer} from "node:http";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import path from "node:path";
import {chromium} from "playwright";

const dir=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(dir,"..");
const server=createServer((req,res)=>{
  const pathname=new URL(req.url,"http://localhost").pathname;
  const files={"/club/":"club/index.html","/club":"club/index.html","/club/index.html":"club/index.html","/club/style.css":"club/style.css","/game-shell.css":"game-shell.css","/club/ludo-immersive.css":"club/ludo-immersive.css","/club/games.js":"club/games.js","/club/naija-ludo.js":"club/naija-ludo.js","/club/ludo-motion.js":"club/ludo-motion.js","/club/ludo-dice-3d.js":"club/ludo-dice-3d.js","/games":"index.html","/index.html":"index.html"};
  const rel=files[pathname];
  if(!rel){res.writeHead(404);res.end("Not found");return;}
  const kind=rel.endsWith(".css")?"text/css":rel.endsWith(".js")?"text/javascript":"text/html";
  res.writeHead(200,{"content-type":kind+"; charset=utf-8","cache-control":"no-store"});
  res.end(readFileSync(path.join(root,rel)));
});
await new Promise(ok=>server.listen(0,"127.0.0.1",ok));
const base="http://127.0.0.1:"+server.address().port;
let browser;
try{
  browser=await chromium.launch({headless:true,channel:"chrome",args:["--no-sandbox"]});
  const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const errors=[];
  page.on("pageerror",e=>errors.push(String(e)));
  await page.goto(base+"/club/?game=ludo");
  await page.locator(".l-cell").first().waitFor();
  await page.waitForFunction(()=>window.NightLudo3D?.ready===true,{timeout:12000});
  mkdirSync("night-play/visual-output",{recursive:true});
  assert.equal(await page.locator(".l-cell").count(),225,"traditional 15x15 board");
  assert.equal(await page.locator("body.ludo-mode").count(),1,"Ludo opens directly into immersive game screen");
  assert.equal(await page.locator(".ludo-hud").isVisible(),true,"in-game player score HUD visible");
  assert.equal(await page.locator(".club-intro").isVisible(),false,"no distracting website hero");
  assert.equal(await page.locator(".game-picks").isVisible(),false,"no huge browser menu above Ludo board");
  assert.equal(await page.locator(".nj-avatar").count(),4,"four original football medallions are drawn");
  assert.equal(await page.locator('.nj-dice-controls').isVisible(),true,"large dice stay visible for touch selection");
  const diceTouchBox=await page.locator('.nj-die-button').first().boundingBox();
  assert.ok(diceTouchBox.width>=44&&diceTouchBox.height>=44,"dice have comfortable touch targets");
  const screenBottom=await page.locator('.info-panel').boundingBox();
  assert.ok(screenBottom.y+screenBottom.height<740,"the board and dice fit the first phone screen");
  const width=await page.locator(".nj-board").boundingBox();
  assert.ok(width.width>340&&width.width<391,"board fills almost entire 390px phone width");
  const top=await page.locator(".nj-board").evaluate(el=>el.getBoundingClientRect().top);
  assert.ok(top<220,"board loads in the first phone viewport");
  await page.screenshot({path:"night-play/visual-output/NAIJA-LUDO-IMMERSIVE-MOBILE.png",fullPage:true});

  assert.equal(await page.locator(".l-base").count(),4,"four coloured homes");
  await page.locator(".nj-board").screenshot({path:"night-play/visual-output/NAIJA-LUDO-BOARD.jpg",type:"jpeg",quality:85});
  assert.equal(await page.locator("[data-piece]").count(),16,"two Naija Ludo players each control two houses of four counters");
  assert.equal(await page.locator(".nj-dice-center .nj-die").count(),2,"two dice inside board");
  assert.equal(await page.locator(".nj-die-button").count(),2,"two tappable large dice");
  const rendererState=await page.evaluate(()=>({ready:window.NightLudo3D.ready,center:!!document.querySelector('.nj-dice-center'),attached:!!document.querySelector('.nj-three-dice-layer')?.parentElement?.matches('.nj-dice-center'),rootReady:document.querySelector('.nj-dice-center')?.dataset.threeReady,canvas:!!document.querySelector('.nj-three-dice-layer')}));
  assert.equal(rendererState.canvas,true,"Ludo centre dice use the Three.js WebGL renderer: "+JSON.stringify(rendererState));
  assert.equal(await page.locator(".nj-dice-controls .nj-three-controls-layer").count(),1,"large tappable dice use the matching Three.js renderer");
  await page.evaluate(()=>window.NightLudo.setTestRolls([6,2]));
  await page.locator('[data-action="ludo-roll"]').first().click();
  assert.ok(await page.locator(".nj-dice-center.is-rolling").count(),"dice visibly tumble");
  await page.waitForTimeout(170);
  const motionA=await page.evaluate(()=>NightLudo3D.inspect());
  await page.screenshot({path:"night-play/visual-output/LUDO-THROW-FLIGHT.jpg",type:"jpeg",quality:85});
  assert.equal(await page.locator('.nj-dice-center .nj-cube').first().evaluate(el=>getComputedStyle(el).filter),'none',"dice shadow must not flatten the 3D cube faces");
  await page.waitForTimeout(130);
  const motionB=await page.evaluate(()=>NightLudo3D.inspect());
  for(const display of ['center','controls'])assert.notDeepEqual(motionA[display][0].rotation,motionB[display][0].rotation,display+' dice must both animate during the same roll');
  await page.screenshot({path:"night-play/visual-output/LUDO-THROW-TUMBLE.jpg",type:"jpeg",quality:85});
  assert.ok(await page.locator(".nj-three-dice-layer").evaluate(el=>el.width>0&&el.height>0),"three-dimensional dice canvas has a live render size");
  await page.locator(".l-token.eligible").first().waitFor({timeout:5000});
  const settled=await page.evaluate(()=>NightLudo3D.inspect());
  for(const display of ['center','controls']){assert.deepEqual(settled[display].map(d=>d.value),[6,2]);assert.ok(settled[display].every(d=>!d.animating),'dice stop tumbling before gameplay unlocks');}
  const shownDice=await page.locator(".nj-dice-controls .nj-die").evaluateAll(elements=>elements.map(el=>el.querySelectorAll(".nj-face-front .nj-pips i.lit").length));
  assert.deepEqual(shownDice,[6,2],"3D dice face graphics must precisely match the dice actually rolled");
  const centerDice=await page.locator(".nj-dice-center .nj-die").evaluateAll(elements=>elements.map(el=>el.querySelectorAll(".nj-face-front .nj-pips i.lit").length));
  assert.deepEqual(centerDice,[6,2],"centre-table dice must show the same results");

  await page.screenshot({path:"night-play/visual-output/NAIJA-LUDO-DICE-READY.jpg",type:"jpeg",quality:76,fullPage:true});
  assert.ok(await page.locator(".l-token.eligible").count()>=4,"six releases a piece from its house");
  await page.locator(".l-token.eligible").first().click();
  await page.waitForFunction(()=>document.querySelector('.nj-center-button[data-ludo-die="1"]')?.disabled===false);
  assert.ok(await page.locator('[data-ludo-die="1"]').count(),"second die remains selectable");
  assert.equal(await page.locator('[data-dice-mode="ludo"]').isDisabled(),true,"dice configuration locked until remaining roll is played");
  assert.equal(await page.locator('[data-ludo-die="1"]').first().isEnabled(),true,"second die becomes playable after releasing a piece with six");
  assert.equal(await page.locator('.nj-center-button.is-used').count(),1,"spent die is marked directly on the board");
  assert.equal(await page.locator('.nj-center-button[aria-pressed="true"]').count(),1,"selected die is marked directly on the board");
  assert.ok(await page.locator('.nj-destination').count()>0,"selected roll shows its possible landing squares");
  await page.locator(".l-cell .l-token.eligible").first().click();
  await page.waitForTimeout(210);
  assert.ok(await page.locator('.nj-motion-layer[data-motion="hop"] .nj-piece').evaluateAll(elements=>elements.some(el=>el.getAnimations().some(a=>a.playState==='running'))),"counter hop survives board redraws");
  await page.screenshot({path:"night-play/visual-output/LUDO-HOP-MOTION.jpg",type:"jpeg",quality:85});
  await page.getByText(/Six! Roll again/).waitFor({timeout:4500});
  await page.screenshot({path:"night-play/visual-output/LUDO-COUNTER-ON-TRACK.jpg",type:"jpeg",quality:85});
  assert.equal(await page.locator('[data-dice-mode="ludo"]').isEnabled(),true,"settings unlock once both dice were used");
  await page.locator("#ludoSettingsBtn").click();
  await page.locator("[data-players=ludo]").selectOption("4");
  assert.equal(await page.locator("[data-piece]").count(),16,"four players each have one house");
  assert.equal(await page.locator('.ludo-hud .player').count(),4,"all four players appear in the scoreboard");
  await page.screenshot({path:"night-play/visual-output/LUDO-FOUR-PLAYERS.jpg",type:"jpeg",quality:85});
  await page.locator('[data-dice-mode="ludo"]').selectOption("1");
  assert.equal(await page.locator(".nj-die-button").count(),1,"one-die classic option works");
  const single=await page.evaluate(()=>NightLudo3D.inspect());
  for(const display of ['center','controls']){assert.equal(single[display][0].x,0,'single die is centered');assert.equal(single[display][1].visible,false,'second die disappears in classic mode');}
  await page.locator('[data-dice-mode="ludo"]').selectOption("2");
  assert.equal(await page.locator(".nj-die-button").count(),2,"two-dice option restored");
  // The Nigerian gameplay settings are interactive and kept between renders.
  await page.locator("#ludoSheetBack").click({position:{x:10,y:50}});
  await page.locator("#ludoSettingsBtn").click();
  assert.equal(await page.locator(".nj-settings").evaluate(el=>el.open),true,"top right settings opens native sheet");
  assert.equal(await page.locator("#ludoSheetBack").isVisible(),true,"sheet has a backdrop");
  await page.locator("#ludoSheetBack").click({position:{x:10,y:50}});
  await page.waitForFunction(()=>!document.querySelector('.nj-settings').open);
  assert.equal(await page.locator(".nj-settings").evaluate(el=>el.open),false,"backdrop closes the sheet after its exit animation");
  await page.locator("#ludoSettingsBtn").click();
  assert.ok(await page.locator(".nj-settings").evaluate(el=>el.open),"options can expand");
  await page.locator('[data-ludo-option="captureFinishes"]').uncheck();
  assert.equal(await page.locator('[data-ludo-option="captureFinishes"]').isChecked(),false,"optional attacker removal disabled");
  assert.equal(await page.locator(".nj-settings").evaluate(el=>el.open),true,"options remain open after editing");
  await page.locator('[data-ludo-option="captureFinishes"]').check();
  await page.locator('[data-ludo-option="barrier"]').uncheck();
  assert.equal(await page.locator('[data-ludo-option="barrier"]').isChecked(),false);
  await page.locator('[data-ludo-option="barrier"]').check();
  await page.locator('[data-ludo-speed]').selectOption("slow");
  assert.equal(await page.locator('[data-ludo-speed]').inputValue(),"slow");
  await page.locator('[data-ludo-option="directCount"]').check();
  assert.equal(await page.locator('[data-ludo-option="directCount"]').isChecked(),true);
  assert.ok(await page.locator(".nj-arrow").count()>=6,"painted direction arrows visible");

  // Assert engine mechanics independently of animation and avoid random rolls.
  const outcomes=await page.evaluate(()=>{
    const a=window.NightLudo;
    const capture=a.create(2);capture.players[0].tokens[0]=15;capture.players[1].tokens[0]=2;
    const result=a.testCapture(capture,0,0);
    const captured=result.hit&&capture.players[0].tokens[0]===56&&capture.players[1].tokens[0]===-1;
    const custom=a.create(2);custom.options.captureFinishes=false;custom.players[0].tokens[0]=15;custom.players[1].tokens[0]=2;
    a.testCapture(custom,0,0);
    const optionalCapture=custom.players[0].tokens[0]===15&&custom.players[1].tokens[0]===-1;
    const safe=a.create(2);safe.players[0].tokens[0]=21;safe.players[1].tokens[0]=8;
    const safetyOn=!a.testCapture(safe,0,0).hit;
    safe.options.safeHouse=false;const safetyOff=a.testCapture(safe,0,0).hit;
    const barrier=a.create(2);barrier.players[0].tokens[0]=12;
    barrier.players[1].tokens[0]=0;barrier.players[1].tokens[1]=0;
    barrier.rolls=[{value:2,used:false}];barrier.selectedDie=0;
    const blocked=!a.testLegal(barrier,0,0,0);
    barrier.options.barrier=false;const open=a.testLegal(barrier,0,0,0);
    barrier.options.barrier=true;barrier.rolls[0].value=6;
    const sixPasses=a.testLegal(barrier,0,0,0);
    return {captured,optionalCapture,safetyOn,safetyOff,blocked,open,sixPasses};
  });
  assert.deepEqual(outcomes,{captured:true,optionalCapture:true,safetyOn:true,safetyOff:true,blocked:true,open:true,sixPasses:true},"Naija Ludo captures, safety, barriers and six-exception");
  await page.locator('[data-ludo-opponent]').selectOption("computer");
  assert.equal(await page.locator('[data-ludo-opponent]').inputValue(),"computer","computer is an actual playable local mode");
  await page.locator('[data-ludo-difficulty]').selectOption("advanced");
  assert.equal(await page.locator('[data-ludo-difficulty]').inputValue(),"advanced","advanced CPU difficulty configurable");
  await page.locator(".nj-settings summary").click();
  await page.evaluate(()=>window.NightLudo.setTestRolls([2,3,2,3]));
  await page.locator('[data-action="ludo-roll"]').first().click();
  await page.getByText(/Computer's turn/).waitFor({timeout:5000});
  assert.equal(await page.locator('.nj-die-button:enabled').count(),0,'human dice controls stay locked during CPU turn');
  await page.getByText(/Red & Yellow's turn/).waitFor({timeout:8500});
  await page.locator("#ludoSettingsBtn").click();
  await page.locator('[data-ludo-opponent]').selectOption("local");
  await page.locator(".nj-settings summary").click();
  await page.waitForFunction(()=>!document.querySelector('.nj-settings').open);
  // Play an actual capture through the UI and inspect the return animation.
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1'));
    const s=NightLudo.create(2,1);s.players[0].tokens[0]=14;s.players[1].tokens[0]=2;
    s.rolls=[{value:1,used:false}];s.last=[1];s.selectedDie=0;s.die=1;s.rolled=true;
    data.ludo=s;localStorage.setItem('night-play-club-v1',JSON.stringify(data));
  });
  await page.reload();
  await page.locator('[data-piece="0-0"]').click();
  const captureFrame=await page.waitForFunction(()=>{
    const el=document.querySelector('.nj-motion-layer[data-motion="capture"] .nj-piece');
    if(!el)return false;const r=el.getBoundingClientRect();
    return {width:r.width,height:r.height,eligible:document.querySelectorAll('.l-token.eligible').length};
  });
  const roundCounter=await captureFrame.jsonValue();
  assert.equal(roundCounter.eligible,0,'input stays locked during capture flight');
  assert.ok(Math.abs(roundCounter.width-roundCounter.height)<1.5,'moving counters keep their circular shape');
  await page.screenshot({path:"night-play/visual-output/LUDO-CAPTURE-FLIGHT.jpg",type:"jpeg",quality:85});
  await page.waitForFunction(()=>!document.querySelector('.nj-motion-layer'));
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[0]===56);
  assert.equal(await page.locator('.nj-yard [data-piece="1-0"]').count(),1,'captured counter settles back in its yard');
  // Finish a match and confirm the final counter animation and victory state.
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1')),s=NightLudo.create(2,1);
    s.players[0].tokens=Array(8).fill(56);s.players[0].tokens[0]=55;
    s.rolls=[{value:1,used:false}];s.last=[1];s.selectedDie=0;s.die=1;s.rolled=true;
    data.ludo=s;localStorage.setItem('night-play-club-v1',JSON.stringify(data));
  });
  await page.reload();await page.locator('[data-piece="0-0"]').click();
  await page.locator('.nj-motion-layer[data-motion="finish"]').waitFor();
  await page.locator('.nj-win-banner').waitFor({timeout:4500});
  await page.screenshot({path:"night-play/visual-output/LUDO-VICTORY-MOTION.jpg",type:"jpeg",quality:85});
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.locator('#ludoSettingsBtn').click();
  await page.locator('.nj-settings summary').click();
  assert.equal(await page.locator('.nj-settings').evaluate(el=>el.open),false,'reduced-motion sheets close immediately');
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1'));data.ludo=NightLudo.create(2,1);
    localStorage.setItem('night-play-club-v1',JSON.stringify(data));
  });
  await page.reload();await page.evaluate(()=>NightLudo.setTestRolls([6]));
  await page.locator('[data-action="ludo-roll"]').first().click();
  await page.locator('[data-piece="0-0"]:enabled').waitFor();
  await page.locator('[data-piece="0-0"]').click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[0]===0);
  assert.equal(await page.locator('.nj-motion-layer').count(),0,'reduced motion plays without animated flights');
  await page.emulateMedia({reducedMotion:'no-preference'});
  // Eight allied counters can occupy one physical square; none may vanish.
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1')),s=NightLudo.create(2,1);
    s.players[0].tokens.fill(0,0,4);s.players[0].tokens.fill(26,4);
    s.rolls=[{value:1,used:false}];s.last=[1];s.selectedDie=0;s.die=1;s.rolled=true;
    data.ludo=s;localStorage.setItem('night-play-club-v1',JSON.stringify(data));
  });
  await page.reload();assert.equal(await page.locator('.nj-board [data-piece]').count(),16,'every stacked counter remains on the board');
  await page.locator('.nj-counter-picker summary').click();
  await page.screenshot({path:'night-play/visual-output/LUDO-STACK-CHOICES.jpg',type:'jpeg',quality:85});
  await page.locator('.nj-counter-picker [data-piece="0-7"]').click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[7]===27);
  // Switching games rolls back an unfinished move and cancels every old hop.
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1')),s=NightLudo.create(2,1);
    s.options.speed='slow';s.players[0].tokens[0]=10;s.rolls=[{value:4,used:false}];s.last=[4];s.selectedDie=0;s.die=4;s.rolled=true;
    data.ludo=s;localStorage.setItem('night-play-club-v1',JSON.stringify(data));
  });
  await page.reload();
  const idleFlights=await page.evaluate(()=>{document.querySelector('.nj-board [data-piece="0-0"]').click();return document.querySelectorAll('.nj-motion-layer').length;});
  assert.equal(idleFlights,0,'early taps during board entrance must not move unchanged counters');
  await page.waitForFunction(()=>document.querySelector('.nj-board [data-piece="0-0"]')?.dataset.progress==='11');
  await page.evaluate(()=>document.querySelector('[data-pick="estate"]').click());
  await page.goto(base+'/club/?game=ludo');
  await page.waitForTimeout(250);
  const recovered=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo);
  assert.equal(recovered.players[0].tokens[0],10);assert.equal(recovered.rolls[0].used,false);assert.equal(recovered.moving,false);
  await page.locator('.nj-board [data-piece="0-0"]').click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[0]===14);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.turn),1,'resumed move spends its die exactly once');
  // A page reload during a hop also resumes the last complete transaction.
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1')),s=NightLudo.create(2,1);
    s.options.speed='slow';s.players[0].tokens[0]=20;s.rolls=[{value:3,used:false}];s.last=[3];s.selectedDie=0;s.die=3;s.rolled=true;
    data.ludo=s;localStorage.setItem('night-play-club-v1',JSON.stringify(data));
  });
  await page.reload();await page.waitForFunction(()=>NightLudo3D.ready);await page.locator('.nj-board [data-piece="0-0"]').click();
  await page.waitForFunction(()=>{const pos=Number(document.querySelector('.nj-board [data-piece="0-0"]')?.dataset.progress);return pos>20&&pos<23;},null,{polling:20,timeout:6000}).catch(async()=>{throw Error('Interrupted reload did not reach an intermediate hop: '+JSON.stringify(await page.evaluate(()=>({progress:document.querySelector('.nj-board [data-piece="0-0"]')?.dataset.progress,status:document.getElementById('status').textContent,state:JSON.parse(localStorage.getItem('night-play-club-v1')).ludo}))));});
  await page.reload();assert.equal(await page.locator('.nj-board [data-piece="0-0"]').getAttribute('data-progress'),'20');
  await page.locator('.nj-board [data-piece="0-0"]').click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[0]===23);
  await page.screenshot({path:'night-play/visual-output/LUDO-RECOVERED-TURN.jpg',type:'jpeg',quality:85});
  await page.locator(".ludo-back").click();
  await page.waitForURL("**/games");
  await page.goto(base+"/club/?game=estate");
  await page.locator(".estate-tile").first().waitFor();
  assert.equal(await page.locator(".estate-tile").count(),40,"original 40-space property game");
  await page.evaluate(()=>Math.random=()=>0.05);
  await page.locator('[data-action="estate-roll"]').click();
  assert.ok(await page.locator('[data-action="estate-buy"]').count(),"landing on unowned property offers purchase");
  await page.locator('[data-action="estate-buy"]').click();
  assert.ok((await page.locator(".score-list").innerText()).includes("₦"),"property economy visible");
  await page.goto(base+"/club/?game=whot");
  assert.equal(await page.locator("#gameTitle").innerText(),"WHOT","Traditional Nigerian name replaces generic Shape Cards");
  assert.ok(await page.locator(".playing-card").count()>=7,"Five-card WHOT hand, market and discard");
  assert.equal(await page.locator(".playing-card.whot-card").count(),6,"Whot face cards use the recognizable numbered red shapes");
  const whotDeck=await page.evaluate(()=>{
    const w=JSON.parse(localStorage.getItem("night-play-club-v1")).whot;
    return {cards:[...w.hands[0],...w.hands[1],...w.deck,...w.discard],player:w.hands[0].length,opponent:w.hands[1].length};
  });
  assert.equal(whotDeck.cards.length,54,"WHOT uses the real 54-card distribution");
  assert.equal(whotDeck.player,5,"WHOT starts with five cards");
  assert.equal(whotDeck.opponent,5);
  const expected=[[1,2,3,4,5,7,8,10,11,12,13,14],[1,2,3,4,5,7,8,10,11,12,13,14],[1,2,3,5,7,10,11,13,14],[1,2,3,5,7,10,11,13,14],[1,2,3,4,5,7,8]];
  for(let s=0;s<5;s++)assert.deepEqual(whotDeck.cards.filter(x=>x.s===s).map(x=>x.n).sort((a,b)=>a-b),expected[s],"WHOT suit "+s+" matches OG distribution");
  assert.equal(whotDeck.cards.filter(x=>x.n===20).length,5);
  await page.waitForTimeout(1100);
  assert.ok(await page.locator('.whot-suit-big').count()>=1,'WHOT has crisp geometric suit SVGs');
  assert.equal(await page.locator('.whot-deck').count(),1,'WHOT market is a real card stack');
  assert.equal(await page.locator('.hand .whot-card').count(),5,'The five-card hand is visible');
  assert.ok(await page.evaluate(()=>window.__nightWhotMotion().deals)>=5,'Initial cards deal with staggered animation');
  console.log("NIGHT_WHOT_GAME_JPEG_BASE64="+(await page.screenshot({type:"jpeg",quality:66,fullPage:true})).toString("base64"));
  const canPlay=await page.locator('.hand .whot-card.playable:not(.whot-wild)').count()>0;
  await page.evaluate(()=>window.__whotPreviousPile=document.querySelector('.card-pile .whot-card'));
  if(canPlay)await page.locator('.hand .whot-card.playable:not(.whot-wild)').first().click();
  else await page.locator('.whot-deck').click();
  await page.waitForFunction(()=>window.__nightWhotMotion().visibleFlights===1,null,{timeout:2000});
  if(canPlay){
    assert.equal(await page.evaluate(()=>document.querySelector('.card-pile .whot-card')===window.__whotPreviousPile),true,'Playing a card keeps the discard mounted until its flight lands');
  }else{
    // A market draw must lay out the new destination before beginning the
    // flight. The single new card is intentionally hidden until arrival.
    assert.equal(await page.locator('.hand .whot-card').count(),6,'Picking adds exactly one pending hand card');
    assert.equal(await page.locator('.hand .whot-card').last().evaluate(el=>el.style.visibility),'hidden','New card face stays concealed under flying card back');
  }
  assert.equal(await page.evaluate(()=>window.__nightWhotMotion().activeFlights),1,'One card animates without full repaint');
  console.log("NIGHT_WHOT_MOTION_JPEG_BASE64="+(await page.screenshot({type:"jpeg",quality:56,fullPage:true})).toString("base64"));
  await page.waitForFunction(()=>window.__nightWhotMotion().visibleFlights===0,null,{timeout:2500});
  if(canPlay)assert.equal(await page.evaluate(()=>document.querySelector('.card-pile .whot-card')===window.__whotPreviousPile),false,'Discard swaps only once the flight ends');
  assert.ok(await page.evaluate(()=>{const m=window.__nightWhotMotion();return m.plays+m.draws>0}),'Move increments hardcoded animation counters');
  // Real Naija Whot dimensions: 80px cards, horizontal swipeable strip.
  await page.waitForTimeout(650);
  for(const n of [5,4,3,2,1,8,12]){
    await page.evaluate(count=>{
      const saved=JSON.parse(localStorage.getItem("night-play-club-v1"));
      const s=saved.whot,pool=[...s.hands[0],...s.deck];
      s.hands[0]=pool.slice(0,count);s.deck=pool.slice(count);
      s.turn=0;s.winner=null;s.pendingWhot=null;
      localStorage.setItem("night-play-club-v1",JSON.stringify(saved));
    },n);
    await page.reload();
    await page.locator('.hand .whot-card').first().waitFor();
    await page.waitForTimeout(75);
    const info=await page.evaluate(()=>{
      const row=document.querySelector('.hand .card-row'),cards=[...row.querySelectorAll('.whot-card')];
      const a=cards[0].getBoundingClientRect(),b=cards.at(-1).getBoundingClientRect(),r=row.getBoundingClientRect();
      return {count:cards.length,width:parseFloat(getComputedStyle(cards[0]).width),
        center:(a.left+b.right)/2,goal:r.left+r.width/2,scroll:row.scrollWidth,client:row.clientWidth,
        justify:getComputedStyle(row).justifyContent,wrap:getComputedStyle(row).flexWrap,
        overflow:document.documentElement.scrollWidth-innerWidth,
        height:document.querySelector('.table-surface').getBoundingClientRect().height};
    });
    assert.equal(info.count,n,'correct hand count');
    assert.ok(Math.abs(info.width-80)<1,'reference 80px mobile cards');
    assert.equal(info.wrap,'nowrap','never wrap a scrolling hand');
    if(n<=3){
      assert.equal(info.justify,'center','short hands centred');
      assert.ok(Math.abs(info.center-info.goal)<2,'centre stable');
    }else{
      assert.equal(info.justify,'flex-start','large hand starts at first card');
      assert.ok(info.scroll>info.client,'large hand can swipe');
    }
    for(let i=0;i<n;i++){
      const card=page.locator('.hand .whot-card').nth(i);
      await card.evaluate(el=>{
        const row=el.closest('.card-row'),a=el.getBoundingClientRect(),b=row.getBoundingClientRect();
        row.scrollLeft+=(a.left+a.width/2)-(b.left+b.width/2);
      });
      await page.waitForTimeout(25);
      const exposed=await card.evaluate(el=>{const r=el.getBoundingClientRect(),p=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return p===el||el.contains(p)});
      if(!exposed){
        const diag=await card.evaluate(el=>{
          const r=el.getBoundingClientRect(),row=el.closest('.card-row'),p=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
          return {rect:{x:r.x,y:r.y,w:r.width,h:r.height},row:{left:row.scrollLeft,width:row.clientWidth,total:row.scrollWidth,rect:row.getBoundingClientRect().toJSON()},point:p?.outerHTML.slice(0,150),card:el.outerHTML.slice(0,180)}
        });
        console.log('WHOT_HAND_CARD_DIAGNOSTIC',n,i,JSON.stringify(diag));
      }
      assert.ok(exposed,'card '+i+' is tappable when scrolled into view');
    }
    assert.ok(info.overflow<=2,'hand does not stretch viewport');
    assert.ok(info.height>=600,'portrait board is full height');
    if([1,3,5,12].includes(n))console.log("NIGHT_WHOT_HAND_"+n+"_JPEG_BASE64="+(await page.screenshot({type:'jpeg',quality:52,fullPage:true})).toString('base64'));
  }

  // Card 20 must never auto-call Circle merely because a hidden select
  // defaulted to the first option. A real in-table decision is required.
  await page.evaluate(()=>{
    const data=JSON.parse(localStorage.getItem('night-play-club-v1')),s=data.whot;
    const groups=[s.hands[0],s.hands[1],s.deck,s.discard];
    const area=groups.find(group=>group.some(card=>card.n===20));
    assertWhot(area);
    const idx=area.findIndex(card=>card.n===20);
    [s.hands[0][0],area[idx]]=[area[idx],s.hands[0][0]];
    s.turn=0;s.called=null;s.winner=null;s.pendingWhot=null;
    localStorage.setItem('night-play-club-v1',JSON.stringify(data));
    function assertWhot(value){if(!value)throw Error('Missing wildcard in saved 54-card deck');}
  });
  await page.reload();
  const preCall=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot);
  assert.equal(preCall.hands[0][0].n,20,'test begins with WHOT 20 in hand');
  await page.locator('.hand [data-card="0"]').click();
  await page.locator('.whot-call-panel').waitFor();
  assert.equal(await page.locator('.whot-call-choice').count(),5,'all five real shapes offered immediately on the table');
  assert.equal(await page.locator('#wishShape').count(),0,'no offscreen/default Android select picker');
  assert.equal(await page.locator('[data-action="card-draw"]').count(),1,'market pile is the only draw action');
  const suspended=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot);
  assert.equal(suspended.pendingWhot,0,'waiting to call the shape survives a render');
  assert.equal(suspended.turn,0,'playing 20 must not pass the turn before the call');
  assert.equal(suspended.hands[0].length,preCall.hands[0].length,'20 stays in hand until a choice is made');
  assert.equal(suspended.discard.length,preCall.discard.length,'pile remains untouched while choosing');
  await page.reload();
  assert.equal(await page.locator('.whot-call-choice').count(),5,'20 picker survives accidental page reload');
  await page.locator('[data-action="whot-call-cancel"]').click();
  assert.equal(await page.locator('.whot-call-choice').count(),0,'cancel returns to hand without spending 20');
  assert.equal(await page.locator('.hand .whot-wild').count()>0,true,'cancel preserves the WHOT card');
  await page.locator('.hand [data-card="0"]').click();
  await page.screenshot({path:'night-play/visual-output/WHOT-20-SHAPE-PICKER.jpg',type:'jpeg',quality:86,fullPage:true});
  await page.locator('[data-whot-call="4"]').click();
  await page.waitForFunction(()=>window.__nightWhotMotion().activeFlights===1,{timeout:2500});
  // Capture the exact shape while the player's completed 20 call is
  // visible: the CPU is permitted to answer immediately afterwards,
  // clearing s.called under real WHOT rules. A delayed read here races it.
  const calledHandle=await page.waitForFunction(()=>{
    const s=JSON.parse(localStorage.getItem('night-play-club-v1')).whot;
    return s.discard.at(-1)?.n===20&&s.called===4?s:false;
  },null,{polling:20,timeout:4500});
  const called=await calledHandle.jsonValue();
  assert.equal(called.called,4,'calling STAR records the exact player choice, not a default');
  assert.equal(called.pendingWhot,null,'call clears after card lands');
  assert.equal(called.hands[0].length,preCall.hands[0].length-1,'20 is consumed once');
  assert.ok(called.called===4,'opponent can see the chosen STAR shape during the call');
  await page.goto(base+"/club/?game=colors");
  assert.ok(await page.locator(".color-card").count()>=8,"colour-matching cards dealt");
  if(await page.locator(".color-card.playable").count())await page.locator(".color-card.playable").first().click();
  else await page.locator('[data-action="card-draw"]').click();
  await page.waitForTimeout(560);
  await page.goto(base+"/club/?game=draw");
  await page.locator('[data-action="draw-ready"]').click();
  assert.ok((await page.locator(".secret").innerText()).includes("Artist's word"),"artist sees word before game");
  await page.locator('[data-action="draw-ready"]').click();
  await page.locator("#drawCanvas").waitFor();
  const cv=await page.locator("#drawCanvas").boundingBox();
  await page.mouse.move(cv.x+10,cv.y+10);await page.mouse.down();await page.mouse.move(cv.x+80,cv.y+80,{steps:3});await page.mouse.up();
  const stroke=await page.locator("#drawCanvas").evaluate(el=>{
    const p=el.getContext('2d').getImageData(40,40,1,1).data;
    return p[3];
  });
  assert.ok(stroke>0,'Draw & Guess paints actual canvas pixels with touch pointer actions');
  assert.equal(await page.locator("#guessWord").count(),0,'Artist draws before guessing; answer field stays hidden');
  await page.locator('[data-action="draw-guess"]').click();
  assert.equal(await page.locator('#guessWord').count(),1,'Handoff reveals the second player guess form');
  assert.ok(await page.locator("#drawCanvas").evaluate(el=>el.getContext('2d').getImageData(40,40,1,1).data[3])>0,'Artwork survives player handoff');
  await page.locator("#guessWord").fill("wrong answer");
  await page.locator('#guessForm button[type="submit"]').click();
  assert.ok(await page.locator("#guessLog li").count(),"wrong guesses produce feedback");
  await page.goto(base+"/club/?game=snakes");
  assert.equal(await page.locator(".snakes-board .square").count(),100);
  await page.locator('[data-action="snakes-roll"]').click();
  assert.ok((await page.locator("#status").innerText()).length>10);
  await page.goto(base+"/club/?game=checkers");
  assert.equal(await page.locator(".checker-square").count(),64);
  assert.equal(await page.locator(".checker-piece").count(),24);
  assert.equal(await page.locator('.night-brand-mark use').getAttribute('href'),'#night-mark','Every tabletop game shares Jigsaw NIGHT logo');
  assert.equal(await page.locator('#settingsButton').isVisible(),true,'Draughts uses top-right settings');
  assert.equal(await page.locator('#gamePicks').isVisible(),false,'Club game-selection menu is inside settings');
  await page.locator('#settingsButton').click();
  assert.equal(await page.locator('#settingsDialog').isVisible(),true,'Full rules and game picker are in settings');
  assert.equal(await page.locator('#help').isVisible(),true,'Current game rules are available');
  await page.locator('#settingsClose').click();
  await page.evaluate(()=>window.__nightInitialCheckerSquare=document.querySelector('[data-checker="40"]'));
  console.log('NIGHT_CLUB_SHELL_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:55,fullPage:true})).toString('base64'));
  assert.equal(await page.locator(".checker-scoreline .checker-seat").count(),2,"Draughts shows both player/turn badges");
  assert.equal(await page.locator(".checker-crown").count(),0,"Only kings display crowns");
  assert.equal(await page.locator("body.checkers-mode").count(),1,"Draughts uses its dedicated tabletop styling");
  const boardSize=await page.locator(".checkerboard").evaluate(el=>{const r=el.getBoundingClientRect();return {width:r.width,height:r.height}});
  assert.ok(boardSize.width>=350&&Math.abs(boardSize.width-boardSize.height)<=2,"Draughts board must fill mobile width and remain square");
  console.log("NIGHT_CHECKERS_VISUAL_JPEG_BASE64="+(await page.locator(".checker-stage").screenshot({type:"jpeg",quality:50})).toString("base64"));
  await page.locator('[data-checker="40"]').click();
  assert.equal(await page.evaluate(()=>window.__nightInitialCheckerSquare===document.querySelector('[data-checker="40"]')),true,'Checkers keeps original square on selection');
  assert.ok(await page.locator(".checker-square.target").count(),"legal move highlighted");
  await page.locator(".checker-square.target").first().click();
  assert.equal(await page.locator(".checker-piece").count(),24);
  for(const name of ["ludo","estate","whot","colors","draw","snakes","checkers"]){
    await page.goto(base+"/club/?game="+name);
    assert.equal(await page.locator("#gameTitle").innerText(),({ludo:"Nigerian Ludo",estate:"NIGHT Estates",whot:"WHOT",colors:"Colour Clash",draw:"Draw & Guess",snakes:"Snakes & Ladders",checkers:"Draughts"})[name]);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),"no sideways mobile scroll on "+name);
    if(["estate","whot","colors","draw","snakes"].includes(name))console.log("NIGHT_CLUB_SCREEN_"+name.toUpperCase()+"_JPEG_BASE64="+(await page.screenshot({type:"jpeg",quality:50,fullPage:true})).toString("base64"));
  }
  await page.goto(base+"/games");
  await page.locator("#view-games").waitFor({state:"visible"});
  assert.equal(await page.locator("[data-club]").count(),6,"Six public Club games; Ludo is archived");
  assert.equal(await page.locator('[data-club="ludo"]').count(),0,"Ludo has no public card");
  assert.equal(await page.locator('[data-pick="ludo"]').count(),0,"Ludo has no Club picker tab");
  await page.goto(base+'/club/?game=ludo');
  assert.equal(await page.locator("#gameTitle").innerText(),"Nigerian Ludo");
  assert.deepEqual(errors,[],"no uncaught browser errors");
  const fallback=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const fallbackErrors=[];fallback.on('pageerror',e=>fallbackErrors.push(String(e)));
  await fallback.route('https://cdn.jsdelivr.net/**',route=>route.abort());
  await fallback.goto(base+'/club/?game=ludo');
  assert.equal(await fallback.evaluate(()=>NightLudo3D.warmup()),false,'missing 3D library uses the CSS fallback');
  await fallback.evaluate(()=>NightLudo.setTestRolls([6,2]));await fallback.locator('[data-action="ludo-roll"]').first().click();
  await fallback.locator('.nj-board [data-piece="0-0"]:enabled').waitFor();
  assert.equal(await fallback.locator('.nj-dice-center .nj-cube').first().evaluate(el=>getComputedStyle(el).visibility),'visible');
  await fallback.locator('.nj-board [data-piece="0-0"]').click();
  await fallback.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[0]===0);
  await fallback.locator('.nj-board [data-piece="0-0"]').click();
  await fallback.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).ludo.players[0].tokens[0]===2);
  assert.deepEqual(fallbackErrors,[],'fallback gameplay produces no browser errors');await fallback.close();
  console.log("PASS NIGHT Play Club: seven games, Ludo dice and counters, Estates purchase, card rules, drawing, checkers, touch-mobile routes");
}finally{if(browser)await browser.close();await new Promise(ok=>server.close(ok));}
