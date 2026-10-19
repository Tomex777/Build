import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
let mockProfile=null;
const routes={'/player-identity.js':'player-identity.js','/party':'party/index.html','/party/':'party/index.html',
'/party/games.js':'party/games.js','/party/questions.js':'party/questions.js','/party/style.css':'party/style.css','/game-shell.css':'game-shell.css','/games':'index.html'};
const server=createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/api/account'){
   res.writeHead(200,{'content-type':'application/json','cache-control':'no-store'});
   res.end(JSON.stringify({linked:!!mockProfile,profile:mockProfile}));return;
 }
 const file=routes[pathname];
 if(!file){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,file)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 const url='http://127.0.0.1:'+server.address().port;
 const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
 const snap=()=>page.evaluate(()=>window.__nightPartyTest.snapshot());
 async function geometry(mode){
  for(const [w,h] of [[320,568],[390,844],[430,932],[768,1024],[1280,860]]){
   await page.setViewportSize({width:w,height:h});
   const z=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth,buttons:[...document.querySelectorAll('.party-vote-option')].map(x=>x.getBoundingClientRect().width)}));
   assert.ok(z.scroll<=z.width+2,'horizontal overflow: '+mode+' '+w+'×'+h+' '+JSON.stringify(z));
   assert.ok(z.buttons.every(x=>x>=90),'vote cards too narrow: '+mode+' '+w+'×'+h);
  }
  await page.setViewportSize({width:390,height:844});
 }
 await page.goto(url+'/party/');
 assert.equal(await page.locator('.party-game-card').count(),6,'six distinct modes in hub');
 assert.equal(await page.locator('.party-online-link').count(),0,'room links are per game, not a duplicate global control');
 assert.equal(await page.locator('.night-brand-mark use').getAttribute('href'),'#night-mark');
 await geometry('hub');
 await page.screenshot({path:path.join(output,'party-hub-390x844.jpg'),type:'jpeg',quality:75});
 console.log('NIGHT_PARTY_HUB_BASE64='+(await page.screenshot({type:'jpeg',quality:55})).toString('base64'));
 for(const mode of ['wyr','mlt','tot','nhie','trivia']){
  await page.goto(url+'/party/?game='+mode);
  assert.equal((await snap()).phase,'setup','each game shows its own setup');
  assert.equal(await page.locator('a.party-online-link').getAttribute('href'),'https://nomi-tomex777.vercel.app/#/live/setup/'+mode,'each multiplayer mode has its own invite-first room destination');
  assert.equal(await page.locator('.party-vote-option').count(),0,'no premature choices');
  await page.locator('#settingsButton').click();
  await page.locator('[data-rounds="5"]').click();
  await page.locator('#settingsClose').click();
  for(const name of ['Alex','Sam']){
   await page.locator('#playerName').fill(name);
   await page.locator('#partyAddPlayer button').click();
  }
  assert.equal(await page.locator('.party-player-pill').count(),2);
  await page.locator('#startParty').click();
  assert.equal((await snap()).phase,'handoff');
  assert.equal((await snap()).state.questions.length,5);
  assert.equal(await page.locator('.party-vote-option').count(),0,'private handoff obscures next question');
  for(let round=0;round<5;round++){
   for(let voter=0;voter<2;voter++){
    assert.equal((await snap()).phase,'handoff');
    await page.locator('[data-action="ready"]').click();
    assert.equal((await snap()).phase,'vote');
    if(mode==='mlt')assert.equal(await page.locator('[data-answer]').count(),2,'two-player Most Likely To must offer two real choices');
    await geometry(mode);
    if(round===0&&voter===0){
     await page.screenshot({path:path.join(output,'party-'+mode+'-390x844.jpg'),type:'jpeg',quality:73});
     console.log('NIGHT_PARTY_'+mode.toUpperCase()+'_BASE64='+(await page.screenshot({type:'jpeg',quality:55})).toString('base64'));
    }
    await page.locator('[data-answer]').first().click();
    const st=await snap();assert.equal(st.state.votes[round][voter]!==null,true,'votes are recorded '+mode);
    if(voter===0)assert.equal(st.phase,'handoff','private vote moves directly to handoff');
   }
   assert.equal((await snap()).phase,'reveal');
   assert.ok((await page.locator('.party-result-row').count())>0,'real tally rendered');
   assert.equal((await snap()).state.votes[round].filter(v=>v!==null).length,2);
   if(round===0){
    await page.reload();
    assert.equal((await snap()).phase,'reveal','resume current reveal after refresh');
    assert.equal((await snap()).state.votes[round].filter(v=>v!==null).length,2,'votes restored');
    await page.screenshot({path:path.join(output,'party-'+mode+'-results.jpg'),type:'jpeg',quality:75});
   }
   await page.locator('[data-action="next"]').click();
  }
  assert.equal((await snap()).phase,'finished','completed five-round match '+mode);
  if(mode==='trivia')assert.equal(await page.locator('.party-leaderboard>div').count(),2);
  await page.locator('[data-action="hub"]').click();
  assert.equal(await page.locator('.party-game-card').count(),6);
 }
 await page.goto(url+'/party/?game=quiz');
 assert.equal(await page.locator('#playerName').count(),0,'host does not enter a player roster');
 assert.equal(await page.locator('#quizOwner').count(),0,'host does not type a separate name');
 assert.ok(await page.locator('.party-online-link').getAttribute('href'),'online hosting clearly routes to Nomi');
 await page.locator('[data-action="editor"]').click();
 assert.equal(await page.locator('.party-editor-question').count(),3);
 await page.locator('#quizTitle').fill('How well do you know Alex?');
 await page.screenshot({path:path.join(output,'party-quiz-editor.jpg'),type:'jpeg',quality:74});
 await page.locator('[data-action="play-quiz"]').click();
 assert.equal((await snap()).phase,'setup','local-only practice must ask names when selected');
 for(const name of ['Alex','Sam']){await page.locator('#playerName').fill(name);await page.locator('#partyAddPlayer button').click()}
 await page.locator('#quizBuilderButton').click();
 await page.locator('[data-action="play-quiz"]').click();
 assert.equal((await snap()).phase,'handoff');
 assert.equal((await snap()).state.questions.length,3);
 await page.locator('[data-action="ready"]').click();
 await page.locator('[data-answer]').first().click();
 assert.equal((await snap()).phase,'handoff');
 await page.reload();
 assert.equal((await snap()).phase,'handoff','quiz answers saved after reload');
 // Real WhatsApp display name, not an input placeholder or decorative badge:
 // it must become the participant in the saved, playable match.
 await page.evaluate(()=>localStorage.clear());
 mockProfile={id:'verified-whatsapp-alice',displayName:'WhatsApp Alice'};
 await page.goto(url+'/party/?game=wyr');
 await page.waitForFunction(()=>window.__nightPartyTest.snapshot().people[0]==='WhatsApp Alice');
 assert.deepEqual((await snap()).people,['WhatsApp Alice'],'verified account auto-joins an empty local lobby');
 assert.equal((await snap()).linkedPartyPlayer.id,mockProfile.id,'participant is bound to stable WhatsApp ID');
 await page.locator('#playerName').fill('Sam');
 await page.locator('#partyAddPlayer button').click();
 await page.locator('#startParty').click();
 assert.deepEqual((await snap()).state.players,['WhatsApp Alice','Sam']);
 mockProfile={...mockProfile,displayName:'Aster'};
 await page.evaluate(()=>window.NightPlayerIdentity.refresh());
 assert.deepEqual((await snap()).state.players,['Aster','Sam'],'server rename rewrites actual active match players');
 await page.reload();
 await page.waitForFunction(()=>window.__nightPartyTest.snapshot().verifiedIdentity?.displayName==='Aster');
 assert.deepEqual((await snap()).state.players,['Aster','Sam'],'verified name persists in saved round, not merely UI');
 await page.evaluate(()=>localStorage.clear());
 mockProfile={id:'different-whatsapp-user',displayName:'Jordan'};
 await page.goto(url+'/party/?game=wyr');
 await page.waitForFunction(()=>window.__nightPartyTest.snapshot().verifiedIdentity?.displayName==='Jordan');
 assert.deepEqual((await snap()).people,['Jordan'],'a different account never inherits the previous name');
 assert.deepEqual(errors,[],'no browser page errors');
 console.log('PASS: Nomi-inspired NIGHT party games, five real private-vote loops, accurate results, quiz creation, reload, five viewports.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
