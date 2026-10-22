'use strict';
// Agent E integration QA for Agent A's isolated Channels config branch.
// Run only against a checkout of A's actual source, not a duplicate mock UI.
// The HTTP API is a deterministic fixture; no real WhatsApp posting occurs.
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('playwright');
const root=path.resolve(process.env.NIGHT_SOURCE_ROOT||path.resolve(__dirname,'../..'));
const output=path.resolve(__dirname,'../../visual-output/channels-agent-e/config');
const name='NIGHT 🍿👨🏿‍🍳 𝙆𝙙𝙧𝙖𝙢𝙖 ودراما';
const A='120363282083849178@newsletter', B='120363282083849179@newsletter';
const giflessPNG='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+i4VsAAAAASUVORK5CYII=';
const configs=new Map();
function initial() {return {revision:0,modules:{memes:false,news:false,anonymous:false,ai:false},
  memes:{mode:'review',categories:['relatable'],dailyLimit:1},
  news:{mode:'review',interests:['casting'],spoilers:'avoid'},
  capabilities:{ai:false}};}
for(const owner of ['A','B'])configs.set(owner,initial());
const answers=new Map(),requests=[],jobs=[];
let brokenConfig=false,ownerARevoked=false,nextBatchCounter=0;
function candidates(batch) {return Array.from({length:4},(_,i)=>({
 id:'fixture_'+batch+'_'+(i+1),title:['Relatable','Old but funny','Anime','Animals'][i]+' sample '+batch,
 category:'relatable',caption:'Editable caption '+(i+1),previewData:giflessPNG,
 publishable:i!==3,reviewOnly:i===3,publishBlockedReason:i===3?'rights_unverified':null,
 sourceName:'Offline fixture, not a live licensed feed',
 sourceUrl:'https://example.org/fixture/'+batch+'/'+i
}));}
const makeBatch=batch=>({batchId:'qa_batch_'+batch+'_00001',candidates:candidates(batch),
 decisions:answers.get(batch)||[]});
function clone(x){return JSON.parse(JSON.stringify(x));}
function isOwn(owner,id){return (owner==='A'&&id===A)||(owner==='B'&&id===B);}
const server=http.createServer(async(req,res)=>{
 const u=new URL(req.url,'http://127.0.0.1'), route=u.pathname;
 if(route==='/site-shell.css'||route==='/channels/config/style.css'||route==='/channels/config/app.js'||route==='/channels/config/index.html'){
  const file=route==='/site-shell.css'?'site-shell.css':route.replace(/^\//,'');
  const target=path.resolve(root,file);
  if(!target.startsWith(root+path.sep)||!fs.existsSync(target)){res.writeHead(404);return res.end('Missing source');}
  res.writeHead(200,{'Content-Type':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
  return res.end(fs.readFileSync(target));
 }
 if(route==='/channels/config/'||route==='/channels/config'){
  res.writeHead(200,{'Content-Type':'text/html'});
  return res.end(fs.readFileSync(path.join(root,'channels/config/index.html')));
 }
 if(route!=='/api/account'){res.writeHead(404);return res.end();}
 const reply=(status,value)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(value));};
 const owner=/(?:^|;\s*)qa_owner=([AB])(?:;|$)/.exec(req.headers.cookie||'')?.[1];
 if(!owner)return reply(401,{error:'No verified login'});
 if(req.method==='GET')return reply(200,{linked:true,profile:{id:owner,displayName:'QA '+owner}});
 let body;
 try {const chunks=[];for await(const b of req)chunks.push(b);body=JSON.parse(Buffer.concat(chunks).toString('utf8'));}
 catch {return reply(400,{error:'Malformed request'});}
 const {action,channelId}=body;
 requests.push({owner,action,channelId,batchId:body.batchId,decision:body.decision});
 if(ownerARevoked&&owner==='A')return reply(403,{error:'Channel access revoked'});
 if(action==='channel:list')return reply(200,{channels:[{id:owner==='A'?A:B,name:owner==='A'?name:'Different owner',status:'connected'}]});
 if(!isOwn(owner,channelId))return reply(403,{error:'Cannot manage another owner channel'});
 const config=configs.get(owner);
 if(action==='channel:config:get')return brokenConfig?reply(503,{error:'Backend config unavailable'}):reply(200,clone(config));
 if(action==='channel:config:update'){
  if(body.expectedRevision!==config.revision)return reply(409,{error:'Outdated revision'});
  for(const [key,value] of Object.entries(body.patch||{})){
   if(key==='modules'){
     for(const [type,on] of Object.entries(value||{})){
       if(!['memes','news','anonymous'].includes(type)||typeof on!=='boolean')
         return reply(400,{error:'Unsupported setting'});
       config.modules[type]=on;
     }
   }else if(key==='memes'||key==='news'){config[key]={...config[key],...value};}
   else return reply(400,{error:'Unsupported patch'});
  }
  config.revision++;return reply(200,clone(config));
 }
 if(action==='channel:jobs:list')return reply(200,{jobs:jobs.filter(j=>j.owner===owner)});
 if(action==='channel:memes:current')return reply(404,{error:'No persisted batch'});
 if(action==='channel:memes:find'){
  if(!config.modules.memes)return reply(403,{error:'Meme module is OFF'});
  const batch=body.next?++nextBatchCounter+1:1;
  return reply(200,makeBatch(batch));
 }
 if(action==='channel:memes:review'){
  if(!config.modules.memes)return reply(403,{error:'Meme module is OFF'});
  const index=/^qa_batch_(\d+)_00001$/.exec(body.batchId)?.[1];
  if(!index||!candidates(Number(index)).some(c=>c.id===body.candidateId))
   return reply(400,{error:'Unknown candidate/batch'});
  const arr=answers.get(Number(index))||[];
  const pos=arr.findIndex(x=>x.candidateId===body.candidateId);
  const saved={candidateId:body.candidateId,decision:body.decision,caption:body.caption};
  if(pos<0)arr.push(saved);else arr[pos]=saved;
  answers.set(Number(index),arr);
  return reply(200,saved);
 }
 if(action==='channel:memes:publish'){
  if(!config.modules.memes)return reply(403,{error:'Meme module is OFF'});
  const all=[...answers.values()].flat().filter(x=>x.decision==='approved');
  // Future backend must receive an immutable selection snapshot across batches.
  const queued=all.map(x=>({id:'job_'+x.candidateId,owner,status:'queued'}));
  jobs.push(...queued);
  return reply(200,{jobs:queued});
 }
 return reply(400,{error:'Unsupported action'});
});
let base='';
function overflow(page){return page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth})).then(v=>
 assert.ok(v.scroll<=v.width+1,'Horizontal overflow '+JSON.stringify(v)));}
async function context(browser,owner,width,height){
 const ctx=await browser.newContext({viewport:{width,height},isMobile:width<600,hasTouch:width<600});
 await ctx.addCookies([{name:'qa_owner',value:owner,url:base}]);return ctx;
}
async function main(){
 if(!fs.existsSync(path.join(root,'channels/config/app.js')))
  throw Error('Agent A config files not present: provide NIGHT_SOURCE_ROOT to a checkout of A branch');
 await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
 base='http://127.0.0.1:'+server.address().port;
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 try{
  for(const width of [320,360,390,412,1280]){
   const ctx=await context(browser,'A',width,width===1280?900:844),page=await ctx.newPage(),errors=[];
   page.on('pageerror',e=>errors.push(e.message));
   await page.goto(base+'/channels/config/?channel='+encodeURIComponent(A));
   await page.getByRole('heading',{name:'Configure channel'}).waitFor();
   await page.locator('#configUI:visible').waitFor();
   for(const module of ['memes','news','anonymous','ai'])
    assert.equal(await page.locator('[data-module="'+module+'"]').isChecked(),false,
      'Every module OFF by default: '+module);
   assert.equal(await page.locator('[data-module="ai"]').isDisabled(),true);
   assert.match(await page.locator('#channelTitle').innerText(),/NIGHT/);
   await overflow(page);
   assert.deepEqual(errors,[]);
   await page.screenshot({path:path.join(output,'config-off-'+width+'.png'),fullPage:true});
   await ctx.close();
  }
  const foreign=await context(browser,'B',390,844),bad=await foreign.newPage();
  await bad.goto(base+'/channels/config/?channel='+encodeURIComponent(A));
  await bad.getByText('Access unavailable').waitFor();
  assert.equal(await bad.locator('#configUI').isVisible(),false);
  await bad.screenshot({path:path.join(output,'foreign-owner-denied.png'),fullPage:true});
  await foreign.close();
  brokenConfig=true;
  const errctx=await context(browser,'A',390,844),err=await errctx.newPage();
  await err.goto(base+'/channels/config/?channel='+encodeURIComponent(A));
  await err.getByText('Configuration unavailable').waitFor();
  assert.equal(await err.locator('#configUI').isVisible(),false);
  await err.screenshot({path:path.join(output,'config-backend-down.png'),fullPage:true});
  await errctx.close();brokenConfig=false;

  const ctx=await context(browser,'A',390,844),page=await ctx.newPage();
  await page.goto(base+'/channels/config/?channel='+encodeURIComponent(A));
  await page.locator('#configUI:visible').waitFor();
  // Exercise the actual visible custom switch, not its 1px accessible input.
  await page.locator('label.switch').filter({has:page.locator('[data-module="memes"]')}).click();
  await page.waitForFunction(()=>document.querySelector('[data-module="memes"]').checked &&
    !document.querySelector('[data-module="memes"]').disabled);
  assert.equal(configs.get('A').modules.memes,true,'Settings confirmed by fixture');
  await page.getByRole('button',{name:'Find memes'}).click();
  await page.locator('.review-card').first().waitFor();
  assert.equal(await page.locator('.review-card').count(),4);
  await page.screenshot({path:path.join(output,'review-four-candidates.png'),fullPage:true});
  const blockedCard=page.locator('.review-card').nth(3);
  assert.equal(await blockedCard.getByRole('button',{name:'Approve'}).isEnabled(),false,
    'Rights-unverified review-only meme cannot be approved for publishing');
  await page.locator('.review-card').first().getByRole('button',{name:'Approve'}).click();
  await page.locator('.review-card').first().getByText('Approved',{exact:true}).waitFor();
  assert.match(await page.locator('#approvedCount').innerText(),/^1 approved/);
  await page.screenshot({path:path.join(output,'review-first-approved.png'),fullPage:true});
  await page.getByRole('button',{name:'Next four'}).click();
  await page.locator('.review-card').first().getByRole('heading',{name:/sample 2/}).waitFor();
  await page.screenshot({path:path.join(output,'review-next-batch.png'),fullPage:true});
  assert.ok((await page.locator('#approvedCount').innerText()).match(/[1-9]\d* approved/),
    'P1: Previous approved choices must remain in visible selection tray across batches');
  assert.equal(await page.getByRole('button',{name:'Publish approved'}).isEnabled(),true,
    'Selections from earlier batches must remain publishable');
  // Revocation must also fail closed even if an already loaded page has buttons.
  ownerARevoked=true;
  await page.getByRole('button',{name:'Publish approved'}).click();
  await page.locator('#reviewNotice:not([hidden])').waitFor();
  assert.equal(jobs.length,0,'No posts can be queued after admin/owner revocation');
  await ctx.close();
  console.log('PASS A config five breakpoints, two owners, OFF-default, review carryover and revocation');
 } catch(err){
  fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({error:String(err.stack||err),requests,answers:[...answers]},null,2));
  throw err;
 } finally{
  await browser.close();
  server.closeAllConnections();
  await new Promise(ok=>server.close(ok));
 }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
