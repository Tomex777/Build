import assert from "node:assert/strict";
import "../strategy/logic-test.mjs";
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
  const files={"/club/":"club/index.html","/club":"club/index.html","/club/index.html":"club/index.html","/club/style.css":"club/style.css","/game-shell.css":"game-shell.css","/club/ludo-immersive.css":"club/ludo-immersive.css","/club/games.js":"club/games.js","/club/naija-ludo.js":"club/naija-ludo.js","/club/ludo-motion.js":"club/ludo-motion.js","/club/ludo-dice-3d.js":"club/ludo-dice-3d.js","/strategy/":"strategy/index.html","/strategy":"strategy/index.html","/strategy/index.html":"strategy/index.html","/strategy/style.css":"strategy/style.css","/strategy/game.js":"strategy/game.js","/strategy/ai.js":"strategy/ai.js","/strategy/ai-worker.js":"strategy/ai-worker.js","/strategy/chess-v0.10.3.js":"strategy/chess-v0.10.3.js","/games":"index.html","/index.html":"index.html"};
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
  assert.equal(await page.locator('.whot-opponent-hand .card-back').count(),1,'CPU back is a clean stacked pile');
  assert.equal((await page.locator('.whot-seat-cpu strong').textContent()).trim(),'5','CPU count remains visible');
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
  // Intelligent WHOT hand replaces the old compulsory horizontal scrolling.
  for(const n of [1,2,3,4,5,6,7,8,10,12,13,16,17,21]){
   await page.evaluate(count=>{
    const all=JSON.parse(localStorage.getItem('night-play-club-v1')),s=all.whot,pool=[...s.hands[0],...s.deck];
    s.hands[0]=pool.slice(0,count);s.deck=pool.slice(count);s.turn=0;s.winner=null;s.pendingWhot=null;s.pendingFinish=null;
    localStorage.setItem('night-play-club-v1',JSON.stringify(all));
   },n);
   await page.reload();await page.locator('.hand .whot-card').first().waitFor();
   const m=await page.evaluate(()=>{
    const row=document.querySelector('.hand .card-row'),hand=row.closest('.hand'),all=[...row.querySelectorAll('.whot-card')];
    const cards=all.filter(x=>getComputedStyle(x).display!=='none'),rects=cards.map(x=>x.getBoundingClientRect());
    const rowR=row.getBoundingClientRect(),tableR=document.querySelector('.table-surface').getBoundingClientRect();
    return {count:all.length,visible:cards.length,mode:hand.dataset.whotLayout,
     scroll:row.scrollWidth,client:row.clientWidth,display:getComputedStyle(row).display,
     rects:rects.map(x=>({left:x.left,top:x.top,right:x.right,width:x.width})),
     gallery:!hand.querySelector('.whot-view-hand').hidden,
     handBottom:hand.getBoundingClientRect().bottom,tableBottom:tableR.bottom,
     overflow:document.documentElement.scrollWidth-innerWidth,
     cornerHits:cards.map(c=>{const r=c.getBoundingClientRect(),p=document.elementFromPoint(r.left+13,r.top+20);return p===c||c.contains(p)})};
   });
   console.log('WHOT_HAND_METRICS',n,JSON.stringify(m));
   assert.equal(m.count,n,'hand contains '+n+' original cards');
   assert.equal(m.mode,n<=4?'spaced':n<=7?'fan':'double','correct hand mode '+n);
   assert.equal(m.display,'block','cards positioned without flex shrink');
   assert.ok(m.scroll-m.client<=8,'hand has no lateral overflow except slight rotated fan corners');
   assert.ok(m.overflow<=2,'never expands page width');
   assert.ok(m.handBottom<=m.tableBottom+3,'hand contained by framed tabletop');
   if(n<=4){
    assert.equal(m.visible,n);
    assert.ok(m.rects.every((x,i)=>!i||x.left>=m.rects[i-1].right-3),'small hands fully exposed');
   }else if(n<=7){
    assert.equal(m.visible,n);
    const step=m.rects[1].left-m.rects[0].left;
    assert.ok(step>=27&&step<m.rects[0].width,'fanned ranks exposed');
    assert.ok(m.cornerHits.every(Boolean),'fan top-left corners clickable');
   }else{
    assert.ok(m.rects.some(r=>r.top-m.rects[0].top>85),'two rows of cards');
    assert.ok(m.visible<=12,'ultra-large hand stays readable');
    assert.equal(m.gallery,n>=13,'gallery control offered for large hands');
    assert.ok(m.cornerHits.every(Boolean),'two-row corners accessible');
   }
   if(n>=13){
    await page.locator('.whot-view-hand').click();
    const gallery=page.locator('.whot-hand-overlay');
    assert.equal(await gallery.locator('.whot-card').count(),n,'full hand gallery shows '+n);
    await gallery.locator('[data-whot-hand-sort]').selectOption('number');
    const galleryDebug=await page.evaluate(()=>{
      const st=JSON.parse(localStorage.getItem('night-play-club-v1')).whot;
      return {hand:st.hands[0].length,ids:[...document.querySelectorAll('.whot-hand-gallery .whot-card')].map(c=>c.dataset.card)};
    });
    console.log('WHOT_GALLERY_DEBUG',n,JSON.stringify(galleryDebug));
    const nums=await page.evaluate(()=>{
     const s=JSON.parse(localStorage.getItem('night-play-club-v1')).whot;
     return [...document.querySelectorAll('.whot-hand-gallery .whot-card')].map(c=>s.hands[0][Number(c.dataset.card)].n);
    });
    assert.ok(nums.every((v,i)=>!i||v>=nums[i-1]),'sorting visually arranges by number');
    await gallery.locator('.whot-hand-close').click();
   }
   if([1,5,7,10,16,21].includes(n))console.log('NIGHT_WHOT_HAND_'+n+'_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:50,fullPage:true})).toString('base64'));
  }

  // Extremely large hand: play the actual selected card from the
  // expanded gallery, keeping the real index and performing its normal flight.
  await page.evaluate(()=>{
    const all=JSON.parse(localStorage.getItem('night-play-club-v1')),s=all.whot;
    const idx=s.hands[0].findIndex(c=>c.n!==20);
    const picked=s.hands[0][idx];
    const top=s.deck.findIndex(c=>c.s===picked.s&&c.n!==20);
    if(idx<0||top<0)throw Error('Cannot stage matching full-gallery play');
    const previousTop=s.discard[s.discard.length-1];s.discard[s.discard.length-1]=s.deck.splice(top,1)[0];s.deck.push(previousTop);
    s.turn=0;s.winner=null;s.pendingWhot=null;s.pendingFinish=null;
    localStorage.setItem('night-play-club-v1',JSON.stringify(all));
    window.__galleryPlayableIndex=idx;
  });
  await page.reload();
  await page.locator('.whot-view-hand').click();
  const selectable=await page.evaluate(()=>{
    const s=JSON.parse(localStorage.getItem('night-play-club-v1')).whot;
    return s.hands[0].findIndex(c=>c.n!==20);
  });
  await page.locator('.whot-hand-gallery .whot-card[data-card="'+selectable+'"]').click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot.hands[0].length===20,null,{timeout:4000});
  assert.equal(await page.locator('.whot-hand-overlay').count(),0,'viewer closes after playing its own indexed card');
  assert.equal(await page.locator('.whot-win-layer').count(),0,'large hand moves do not trigger false victory');
  // Market cards still land naturally in the new hand, even when the
  // collapsed board displays only a legible 16-card preview.
  await page.evaluate(()=>{
    const all=JSON.parse(localStorage.getItem('night-play-club-v1'));all.whot.turn=0;
    localStorage.setItem('night-play-club-v1',JSON.stringify(all));
  });
  await page.reload();
  const priorCards=await page.locator('.hand .whot-card').count();
  await page.locator('.whot-deck').click();
  await page.waitForFunction(old=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot.hands[0].length===old+1,priorCards,{timeout:1700});
  await page.waitForFunction(()=>window.__nightWhotMotion().activeFlights===0,null,{timeout:1800});
  assert.equal(await page.locator('.whot-view-hand').isVisible(),true,'large hand still exposes all cards through view control');

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
  await page.locator('.hand [data-card="0"]').click({position:{x:13,y:20}});
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
  await page.locator('.hand [data-card="0"]').click({position:{x:13,y:20}});
  for(const width of [320,390,412]){
   await page.setViewportSize({width,height:844});
   const pickerBounds=await page.evaluate(()=>{
    const table=document.querySelector('.table-surface').getBoundingClientRect();
    const panel=document.querySelector('.whot-call-panel').getBoundingClientRect();
    const choices=[...document.querySelectorAll('.whot-call-choice')].map(el=>{
     const r=el.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width,height:r.height};
    });
    return {panel:{left:panel.left,right:panel.right,top:panel.top,bottom:panel.bottom},
     table:{left:table.left,right:table.right,top:table.top,bottom:table.bottom},choices};
   });
   assert.ok(pickerBounds.panel.left>=pickerBounds.table.left-2&&pickerBounds.panel.right<=pickerBounds.table.right+2,
    'WHOT picker stays within table at '+width+': '+JSON.stringify(pickerBounds));
   assert.ok(pickerBounds.choices.every(r=>r.left>=pickerBounds.panel.left&&r.right<=pickerBounds.panel.right&&r.width>=62&&r.height>=70),
    'all five WHOT 20 choices fully visible and sized for touch at '+width);
   if(width===320){
    await page.waitForFunction(()=>parseFloat(getComputedStyle(document.querySelector('.whot-call-panel')).opacity)>=.99,null,{timeout:2000});
    await page.screenshot({path:'night-play/visual-output/WHOT-20-SHAPE-PICKER-320.jpg',type:'jpeg',quality:83,fullPage:true});
   }
  }
  await page.setViewportSize({width:390,height:844});
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
  await page.waitForFunction(()=>window.__nightCheckersQA().busy===false&&window.__nightCheckersQA().moving===0);
  assert.equal(await page.locator(".checkerboard .checker-piece").count(),24,'a finished legal move preserves 24 pieces');
  // A second *actual* match versus CPU: check its move, not just its label.
  await page.locator('[data-checker-mode="computer"]').click();
  assert.equal(await page.evaluate(()=>window.__nightCheckersQA().mode),'computer');
  assert.equal(await page.locator('.checker-piece').count(),24,'switching to CPU starts a valid new match');
  const beforeCPU=await page.evaluate(()=>JSON.stringify(window.__nightCheckersQA().pieces));
  await page.locator('[data-checker="40"]').click();
  await page.locator('.checker-square.target').first().click();
  await page.waitForFunction(before=>{
   const s=window.__nightCheckersQA();
   return s.turn==='w'&&!s.busy&&JSON.stringify(s.pieces)!==before&&s.moving===0;
  },beforeCPU,{timeout:6000});
  assert.equal(await page.locator('.checker-piece').count(),24,'CPU advances without inventing or deleting a piece');
  assert.equal(await page.locator('[data-checker-mode="local"]').count(),1,'CPU mode may be changed back to local');
  await page.screenshot({path:'night-play/visual-output/CHECKERS-CPU-AFTER-MOVES.jpg',type:'jpeg',quality:78});
  await page.locator('[data-checker-mode="local"]').click();
  assert.equal(await page.evaluate(()=>window.__nightCheckersQA().mode),'local');

  // Real multi-jump rules, mandatory capture priority and winning state.
  await page.evaluate(()=>{
   const saved=JSON.parse(localStorage.getItem('night-play-club-v1'));
   const cells=Array(64).fill(null);
   cells[40]='w';cells[44]='w';cells[33]='b';cells[19]='b';
   saved.checkers={cells,turn:'w',selected:null,forced:null,winner:null,mode:'local',msg:'Capture chain test.'};
   localStorage.setItem('night-play-club-v1',JSON.stringify(saved));
  });
  await page.reload();
  await page.locator('[data-checker="44"]').click();
  assert.equal(await page.locator('.checker-square.target').count(),0,'mandatory capture blocks ordinary piece moves');
  await page.locator('[data-checker="40"]').click();
  assert.equal(await page.locator('[data-checker="26"].target').count(),1,'only capture landing may be chosen');
  await page.locator('[data-checker="26"]').click();
  await page.waitForFunction(()=>{
   const q=window.__nightCheckersQA();
   return !q.busy&&q.pieces[26]==='w'&&q.pieces[33]===null;
  },null,{timeout:4000});
  assert.equal(await page.locator('[data-checker="12"].target').count(),1,'the same piece must continue the capture chain');
  await page.locator('[data-checker="12"]').click();
  await page.waitForFunction(()=>window.__nightCheckersQA().winner==='w'&&!window.__nightCheckersQA().busy,null,{timeout:4000});
  assert.equal(await page.locator('.checker-piece').count(),2,'both captured discs are removed');
  assert.ok((await page.locator('.checker-game-call strong').innerText()).includes('White wins'),'winning the last forced capture ends the game');
  await page.screenshot({path:'night-play/visual-output/CHECKERS-CHAIN-CAPTURE-WIN.jpg',type:'jpeg',quality:80});

  // Promotion creates a real king, and the king can travel backwards.
  await page.evaluate(()=>{
   const saved=JSON.parse(localStorage.getItem('night-play-club-v1'));
   const cells=Array(64).fill(null);
   cells[10]='w';cells[49]='b';
   saved.checkers={cells,turn:'w',selected:null,forced:null,winner:null,mode:'local',msg:'Crowning test.'};
   localStorage.setItem('night-play-club-v1',JSON.stringify(saved));
  });
  await page.reload();
  await page.locator('[data-checker="10"]').click();
  await page.locator('[data-checker="1"]').click();
  await page.waitForFunction(()=>window.__nightCheckersQA().pieces[1]==='W'&&!window.__nightCheckersQA().busy,null,{timeout:4000});
  assert.equal(await page.locator('.checker-crown').count(),1,'the promoted disc has a proper crown SVG');
  await page.locator('[data-checker="49"]').click();
  await page.locator('[data-checker="56"]').click();
  await page.waitForFunction(()=>window.__nightCheckersQA().turn==='w'&&!window.__nightCheckersQA().busy,null,{timeout:4000});
  await page.locator('[data-checker="1"]').click();
  assert.equal(await page.locator('[data-checker="10"].target').count(),1,'crowned white king can legally move backwards');
  await page.screenshot({path:'night-play/visual-output/CHECKERS-KING-BACKWARD.jpg',type:'jpeg',quality:80});
  for(const name of ["ludo","estate","whot","colors","draw","snakes","checkers"]){
    await page.goto(base+"/club/?game="+name);
    assert.equal(await page.locator("#gameTitle").innerText(),({ludo:"Nigerian Ludo",estate:"NIGHT Estates",whot:"WHOT",colors:"Colour Clash",draw:"Draw & Guess",snakes:"Snakes & Ladders",checkers:"Draughts"})[name]);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),"no sideways mobile scroll on "+name);
    if(["estate","whot","colors","draw","snakes"].includes(name))console.log("NIGHT_CLUB_SCREEN_"+name.toUpperCase()+"_JPEG_BASE64="+(await page.screenshot({type:"jpeg",quality:50,fullPage:true})).toString("base64"));
  }

  // Chess is a complete local game rather than the planned crossplay placeholder.
  let chessWorkerStarts=0;
  page.on('worker',worker=>{if(worker.url().includes('/strategy/ai-worker.js'))chessWorkerStarts++});
  await page.goto(base+'/strategy/');
  assert.equal(await page.locator('[data-square]').count(),64,'Chess draws all board squares');
  assert.equal(await page.locator('.piece').count(),32,'Chess starts with 32 original SVG pieces');
  assert.equal(await page.evaluate(()=>window.__nightChessQA().mode),'computer');
  await page.locator('[data-square="e2"]').click();
  assert.equal(await page.locator('[data-square="e4"].legal').count(),1,'legal pawn target is visible');
  assert.equal(await page.locator('[data-square="e5"].legal').count(),0,'illegal jump is not offered');
  await page.locator('[data-square="e4"]').click();
  await page.waitForFunction(()=>{
   const s=window.__nightChessQA();
   return s.moves.length===2&&!s.busy&&s.turn==='w';
  },null,{timeout:10000});
  assert.equal(await page.locator('.piece').count(),32,'CPU must make a legal response');
  assert.ok(chessWorkerStarts>0,'computer search executes in a real Web Worker, not on the UI thread');
  await page.locator('#cpuDifficulty').selectOption('hard');
  assert.equal(await page.evaluate(()=>window.__nightChessQA().difficulty),'hard','difficulty selection changes CPU policy');
  await page.reload();
  assert.equal(await page.locator('#cpuDifficulty').inputValue(),'hard','chosen difficulty survives reload');
  await page.locator('#newMatch').click();
  await page.locator('[data-square="e2"]').click();
  await page.locator('[data-square="e4"]').click();
  await page.waitForFunction(()=>window.__nightChessQA().moves.length===1&&!window.__nightChessQA().busy);
  await page.locator('#undo').click();
  await page.waitForTimeout(450);
  assert.equal(await page.evaluate(()=>window.__nightChessQA().moves.length),0,'undo cancels a pending computer move');
  await page.locator('#cpuDifficulty').selectOption('normal');
  await page.locator('#newMatch').click();
  await page.locator('[data-square="e2"]').click();
  await page.locator('[data-square="e4"]').click();
  await page.waitForFunction(()=>window.__nightChessQA().moves.length===2&&!window.__nightChessQA().busy,null,{timeout:12000});

  const persistedFen=await page.evaluate(()=>window.__nightChessQA().fen);
  await page.reload();
  await page.locator('.piece').first().waitFor();
  assert.equal(await page.evaluate(()=>window.__nightChessQA().fen),persistedFen,'a real position with castling rights resumes');
  for(const width of [320,360,390,412,900]){
   await page.setViewportSize({width,height:844});
   const measurements=await page.evaluate(()=>{
    const board=document.querySelector('.chess-board').getBoundingClientRect();
    return {square:Math.abs(board.width-board.height)<2,overflow:document.documentElement.scrollWidth>innerWidth+2};
   });
   assert.equal(measurements.square,true,'Chess board remains square on '+width);
   assert.equal(measurements.overflow,false,'Chess has no horizontal overflow on '+width);
   if(width===320||width===900)await page.screenshot({path:'night-play/visual-output/CHESS-'+width+'-RESPONSIVE.jpg',type:'jpeg',quality:80,fullPage:true});
  }
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'night-play/visual-output/CHESS-CPU-MOBILE.jpg',type:'jpeg',quality:82,fullPage:true});
  await page.locator('#vsLocal').click();
  assert.equal(await page.evaluate(()=>window.__nightChessQA().mode),'local','two-player mode is clearly separate from CPU');
  const playChess=async(from,to,index)=>{
   await page.locator('[data-square="'+from+'"]').click();
   assert.equal(await page.locator('[data-square="'+to+'"].legal').count(),1,from+' to '+to+' must be a legal move');
   await page.locator('[data-square="'+to+'"]').click();
   await page.waitForFunction(n=>window.__nightChessQA().moves.length===n&&!window.__nightChessQA().busy,index,{timeout:4000});
  };
  await playChess('e2','e4',1);await playChess('e7','e5',2);
  await playChess('f1','c4',3);await playChess('b8','c6',4);
  await playChess('d1','h5',5);await playChess('g8','f6',6);
  await playChess('h5','f7',7);
  assert.equal(await page.evaluate(()=>window.__nightChessQA().gameOver),true,'checkmate ends a complete played match');
  assert.ok((await page.locator('#matchStatus').innerText()).includes('White wins by checkmate'));
  await page.screenshot({path:'night-play/visual-output/CHESS-SCHOLARS-MATE.jpg',type:'jpeg',quality:83,fullPage:true});
  await page.locator('#newMatch').click();
  assert.equal(await page.evaluate(()=>window.__nightChessQA().moves.length),0,'restart clears moves');
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
