'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs'),http=require('node:http'),path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve(__dirname,'..');
const mime={'.html':'text/html','.css':'text/css','.js':'application/javascript'};
const server=http.createServer((req,res)=>{
 const u=new URL(req.url,'http://localhost'),routes={'/club/':'club/index.html','/club':'club/index.html','/game-shell.css':'game-shell.css','/club/style.css':'club/style.css','/club/ludo-immersive.css':'club/ludo-immersive.css','/club/games.js':'club/games.js','/club/naija-ludo.js':'club/naija-ludo.js','/club/ludo-motion.js':'club/ludo-motion.js','/club/ludo-dice-3d.js':'club/ludo-dice-3d.js'};
 const rel=routes[u.pathname];if(!rel){res.writeHead(404);res.end('not found');return}
 res.writeHead(200,{'content-type':mime[path.extname(rel)]||'text/plain','cache-control':'no-store'});
 res.end(fs.readFileSync(path.join(root,rel)));
});
async function hand(page,own,opponent,top){
 await page.evaluate(({own,opponent,top})=>{
  const key='night-play-club-v1',all=JSON.parse(localStorage.getItem(key));
  const s=all.whot,deck=[...s.hands[0],...s.hands[1],...s.deck,...s.discard];
  const pull=card=>{const i=deck.findIndex(c=>c.s===card.s&&c.n===card.n);if(i<0)throw Error('missing '+JSON.stringify(card));return deck.splice(i,1)[0]};
  s.hands=[own.map(pull),opponent.map(pull)];s.discard=[pull(top)];s.deck=deck;
  s.turn=0;s.winner=null;s.called=null;s.pendingWhot=null;s.pendingFinish=null;s.pendingPenalty=null;s.calledLast=[false,false];
  all.whot=s;localStorage.setItem(key,JSON.stringify(all));
 },{own,opponent,top});
 await page.reload();
 await page.locator('.whot-scoreboard').waitFor({state:'visible'});
}
async function waitUnblocked(page){await page.waitForFunction(()=>!window.__nightWhotMotion().busy,null,{timeout:2500})}
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  for(const width of [390,1280]){
   const page=await browser.newPage({viewport:{width,height:844},isMobile:width<600,hasTouch:width<600});
   const errors=[];page.on('pageerror',e=>errors.push(e.stack));
   await page.route('https://cdn.jsdelivr.net/**',r=>r.abort());
   await page.goto('http://127.0.0.1:'+server.address().port+'/club/?game=whot');
   await page.locator('.whot-scoreboard').waitFor();
   const ui=await page.evaluate(()=>({
    page:getComputedStyle(document.body).backgroundColor,
    nav:getComputedStyle(document.querySelector('.top')).backgroundColor,
    grid:getComputedStyle(document.querySelector('.whot-grid-icon')).display,
    arrow:getComputedStyle(document.querySelector('.night-back .night-ico')).display,
    settingsSwitcher:getComputedStyle(document.querySelector('#gamePicks')).display
   }));
   assert.equal(ui.page,'rgb(247, 247, 247)','restore original NIGHT light site');
   assert.equal(ui.nav,'rgb(255, 255, 255)','website header remains light, not green');
   assert.equal(ui.arrow,'none','disliked back-arrow replaced');
   assert.equal(ui.grid,'grid','games grid icon shown');
   assert.equal(ui.settingsSwitcher,'none','WHOT settings do not switch to other games');
   await page.locator('#settingsButton').click();
   assert.equal(await page.locator('#whotModeConfig').isVisible(),true,'WHOT gear offers game-specific options');
   assert.equal(await page.locator('[data-whot-option="strictFinish"]').isChecked(),true,'strict check-up is the default');
   await page.locator('#settingsClose').click();
   // Last Five Square must not produce an instant win. Market draw restores
   // a card and game continues, even if opponent is penalized.
   await hand(page,[{s:3,n:5}],[{s:0,n:3},{s:0,n:4},{s:0,n:7},{s:1,n:11},{s:2,n:10}],{s:3,n:3});
   await page.locator('.hand .whot-card').first().click();
   await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot.pendingFinish===0,null,{timeout:3500});
   const pending=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot);
   assert.equal(pending.winner,null,'final pick-three MUST NOT win strict house mode');
   assert.equal(pending.hands[0].length,0,'final special reached zero but round stays open');
   assert.equal(pending.pendingPenalty.amount,3,'the five still carries a Pick Three effect');
   assert.equal(await page.locator('.whot-win-layer').count(),0,'no victory banner until valid check-up');
   assert.equal(await page.locator('.whot-deck').isEnabled(),true,'market offered to restore a card');
   assert.match(await page.locator('.whot-turn-indicator').innerText(),/PICK TO CONTINUE/);
   await page.locator('.whot-deck').click();
   await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot.hands[0].length===1,null,{timeout:1600});
   const resumed=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot);
   assert.equal(resumed.pendingFinish,null,'finish restriction cleared after market');
   assert.equal(resumed.winner,null,'round still playable after market');
   // Normal last card does win.
   await hand(page,[{s:3,n:7}],[{s:0,n:3},{s:0,n:4},{s:0,n:8},{s:1,n:11},{s:2,n:10}],{s:3,n:3});
   await page.locator('.hand .whot-card').click();
   await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot.winner===0,null,{timeout:2800});
   assert.equal(await page.locator('.whot-win-layer.player-victory').count(),1,'legal ordinary final card wins');
   assert.equal(await page.locator('.whot-rematch').innerText(),'Play again','remove extra arrow from victory action');
   // Penalty defence: opponent can stack a 2, player must pick four.
   await page.locator('.whot-rematch').click();
   await hand(page,[{s:3,n:2},{s:1,n:10}],[{s:0,n:2},{s:0,n:4},{s:1,n:7},{s:1,n:11},{s:2,n:10}],{s:3,n:3});
   await page.locator('.hand .whot-card[data-card="0"]').click();
   await page.waitForFunction(()=>{
     const x=JSON.parse(localStorage.getItem('night-play-club-v1')).whot;
     return x.pendingPenalty?.amount===4&&x.turn===0;
   },null,{timeout:6500});
   const stacked=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-club-v1')).whot);
   assert.equal(stacked.hands[1].length,4,'CPU defended with two instead of picking');
   assert.equal(stacked.pendingPenalty.target,0,'four-card pickup comes back to player');
   await page.locator('.whot-deck').click();
   await page.waitForFunction(()=>{
     const x=JSON.parse(localStorage.getItem('night-play-club-v1')).whot;return x.hands[0].length===5&&x.pendingPenalty===null;
   },null,{timeout:2200});
   // Career tracks promotions. Settings change applies on a new clean round.
   await page.locator('#settingsButton').click();
   await page.locator('[data-whot-option="mode"]').selectOption('career');
   await page.locator('#whotApplySettings').click();
   assert.equal(await page.locator('#whotModeBadge').innerText(),'KINDERGARTEN');
   await page.evaluate(()=>{localStorage.setItem('night-play-whot-career-v1',JSON.stringify({wins:2,losses:0,streak:2,bestStreak:2,tournamentWins:[0,0]}))});
   await page.reload();
   await hand(page,[{s:3,n:7}],[{s:0,n:3},{s:0,n:4},{s:0,n:8},{s:1,n:11},{s:2,n:10}],{s:3,n:3});
   await page.locator('.hand .whot-card').click();
   await page.waitForFunction(()=>JSON.parse(localStorage.getItem('night-play-whot-career-v1')).wins===3,null,{timeout:2800});
   assert.equal(await page.locator('#whotModeBadge').innerText(),'PRIMARY','promotion from Kindergarten after third career victory');
   assert.deepEqual(errors,[],'no browser exceptions in gameplay & menu');
   console.log('WHOT_RULES_PASS',width,JSON.stringify({strict:true,penaltyStack:true,careerPromotion:true,nightSite:true}));
   if(width===390&&process.env.WHOT_RULES_IMAGE==='1'){
     const shot=(await page.screenshot({type:'jpeg',quality:51,fullPage:true})).toString('base64');
     console.log('WHOT_CAREER_IMAGE='+shot);
   }
   await page.close();
  }
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error('WHOT_RULES_FAIL',e.stack||e);process.exitCode=1});
