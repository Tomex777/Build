import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const files={'/party/room/':'party/room/index.html','/party/room':'party/room/index.html','/party/room/room.js':'party/room/room.js','/party/style.css':'party/style.css','/player-identity.js':'player-identity.js','/party/questions.js':'party/questions.js','/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
  const name=files[new URL(req.url,'http://localhost').pathname];
  if(!name){res.writeHead(404);res.end();return}
  res.writeHead(200,{'content-type':name.endsWith('.css')?'text/css':name.endsWith('.js')?'text/javascript':'text/html'});
  res.end(readFileSync(path.join(root,name)));
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
const address='http://127.0.0.1:'+server.address().port;
const game={code:'ABCDEFGH',mode:'wyr',hostName:'Verified Host',phase:'lobby',round:0,questions:windowQuestions(),players:[],votes:new Map()};
function windowQuestions(){return Array.from({length:5},(_,i)=>({prompt:'Round '+(i+1)+': Which adventure?',choices:['Ocean','Space'],correct:null}))}
function snapshot(actor){
  const host=actor==='host',me=game.players.find(p=>p.id===actor)?.id||null;
  const votes=game.votes.get(game.round)||new Map();
  const revealed=game.phase==='reveal'||game.phase==='finished';
  return {code:game.code,mode:game.mode,host,hostName:game.hostName,
    me,phase:game.phase,round:game.round,total:5,players:game.players.slice(),
    question:game.phase==='lobby'?null:game.questions[game.round],
    voted:me?votes.has(me):false,votedCount:votes.size,
    results:revealed?[...Array(2)].map((_,i)=>[...votes.values()].filter(a=>a===i).length):null,
    scores:revealed?game.players.map(p=>({...p,points:0})):null,expiresAt:Date.now()+8000000}
}
async function mocked(route,actor) {
  const req=route.request();
  if(req.method()==='GET'){return route.fulfill({json:{linked:actor==='host',profile:actor==='host'?{id:'host',displayName:'Verified Host'}:null}})}
  const {action,...data}=req.postDataJSON();
  const reply=(code,body)=>route.fulfill({status:code,json:body});
  if(action==='party:create'){assert.equal(actor,'host');assert.equal(data.mode,'wyr');assert.equal(data.questions.length,5);return reply(200,snapshot(actor))}
  if(action==='party:join'){
    if(game.phase!=='lobby')return reply(409,{error:'Already started'});
    if(!game.players.some(x=>x.id===actor))game.players.push({id:actor,name:data.guestName});
    return reply(200,snapshot(actor));
  }
  if(!game.players.some(x=>x.id===actor)&&actor!=='host')return reply(403,{error:'Join via invitation first.'});
  if(action==='party:state')return reply(200,snapshot(actor));
  if(action==='party:start'){assert.equal(actor,'host');assert.equal(game.players.length,2);game.phase='vote';return reply(200,snapshot(actor))}
  if(action==='party:vote'){
    const votes=game.votes.get(game.round)||new Map();votes.set(actor,data.answer);game.votes.set(game.round,votes);
    if(votes.size===game.players.length)game.phase='reveal';return reply(200,snapshot(actor))
  }
  if(action==='party:advance'){assert.equal(game.phase,'reveal');game.round++;game.phase=game.round>=5?'finished':'vote';if(game.phase==='finished')game.round=4;return reply(200,snapshot(actor))}
  return reply(400,{error:'Unexpected action: '+action});
}
let browser;
try {
  browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
  const errors=[],contexts=[];
  async function pageFor(actor,viewport={width:390,height:844}){
    const context=await browser.newContext({viewport,hasTouch:true,isMobile:true});
    contexts.push(context);
    await context.route('**/api/account',route=>mocked(route,actor));
    const page=await context.newPage();
    page.on('pageerror',e=>errors.push(actor+': '+String(e)));
    return page;
  }
  const host=await pageFor('host'),one=await pageFor('guest-1'),two=await pageFor('guest-2');
  const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
  const hostURL=address+'/party/room/?game=wyr',invite=address+'/party/room/?code='+game.code;
  await host.goto(hostURL);
  await host.locator('.party-room-code').waitFor();
  assert.equal(await host.locator('.party-room-code').textContent(),game.code);
  assert.equal(await host.locator('.party-player-pill').count(),0,'host is not added as a player');
  await host.screenshot({path:path.join(output,'party-online-host-lobby.jpg'),type:'jpeg',quality:78});
  await one.goto(invite);await two.goto(invite);
  assert.equal(await one.locator('#guestName').count(),1,'joiner enters name, not host');
  await one.screenshot({path:path.join(output,'party-online-guest-join.jpg'),type:'jpeg',quality:78});
  await one.locator('#guestName').fill('Aster');
  await one.locator('#joinForm button').click();
  await two.locator('#guestName').fill('Robin');
  await two.locator('#joinForm button').click();
  assert.equal(game.players.length,2);
  await host.waitForFunction(()=>document.querySelectorAll('.party-player-pill').length===2,{timeout:7000});
  await host.locator('[data-action="start"]').click();
  await one.locator('[data-answer="0"]').waitFor({timeout:7000});
  await two.locator('[data-answer="1"]').waitFor({timeout:7000});
  await one.screenshot({path:path.join(output,'party-online-real-vote.jpg'),type:'jpeg',quality:78});
  for(let round=0;round<5;round++){
    for(const [p,choice] of [[one,0],[two,1]]){
      await p.locator('[data-answer="'+choice+'"]').click();
    }
    await host.locator('[data-action="advance"]').waitFor({timeout:7000});
    if(round===0)await host.screenshot({path:path.join(output,'party-online-reveal.jpg'),type:'jpeg',quality:78});
    await host.locator('[data-action="advance"]').click();
    if(round<4)await Promise.all([one.locator('[data-answer="0"]').waitFor({timeout:7000}),two.locator('[data-answer="1"]').waitFor({timeout:7000})]);
  }
  assert.equal(game.phase,'finished');
  await host.locator('.party-summary').waitFor();
  for(const [w,h] of [[320,568],[390,844],[430,932],[768,1024],[1280,860]]){
    await host.setViewportSize({width:w,height:h});
    const x=await host.evaluate(()=>({scroll:document.documentElement.scrollWidth,viewport:innerWidth}));
    assert.ok(x.scroll<=x.viewport+2,'online horizontal overflow '+w+'×'+h+' '+JSON.stringify(x));
  }
  assert.deepEqual(errors,[],'no browser script failures');
  console.log('PASS: live NIGHT room host and two remote joiners completed five real browser-played rounds, reveals and five viewports');
  for(const context of contexts)await context.close();
}finally{if(browser)await browser.close();await new Promise(ok=>server.close(ok))}
