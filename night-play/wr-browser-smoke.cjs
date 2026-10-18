'use strict';
/* WR regression: no public mock publishing and a real, styled online-match
   leave confirmation. Exercise actual mobile Chromium controls, not grep. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require('playwright');
const root=__dirname;
const routes={
 '/anonymous/':'anonymous/index.html',
 '/anonymous':'anonymous/index.html',
 '/multiplayer/ttt/':'multiplayer/ttt/index.html',
 '/multiplayer/ttt':'multiplayer/ttt/index.html',
 '/multiplayer/ttt/app.js':'multiplayer/ttt/app.js'
};
const server=http.createServer((req,res)=>{
 const file=routes[new URL(req.url,'http://localhost').pathname];
 if(!file){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':file.endsWith('.js')?'text/javascript':'text/html','cache-control':'no-store'});
 res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto(base+'/anonymous/');
  assert.equal(await page.locator('#anonymousUnavailableHeading').isVisible(),true,
   'Anonymous Nights publicly shows the verified-unavailable state');
  assert.equal(await page.locator('#heroPrimary').isVisible(),false,
   'mock session opening must not be publicly clickable');
  assert.equal(await page.locator('#submitMessage').isVisible(),false,
   'unverified visitors cannot submit into browser-local storage');
  assert.equal(await page.locator('.anon-back').getAttribute('href'),'/channels/');
  await page.goto(base+'/anonymous/join/fake-token');
  // This fixture route is intentionally absent; production rewrites to the
  // same gated page, checked separately in the deployed vercel.json contract.
  await page.goto(base+'/anonymous/?design=1');
  assert.equal(await page.locator('#anonymousUnavailable').isVisible(),false);
  assert.equal(await page.locator('#heroPrimary').isVisible(),true,
   'explicit design review preserves the approved private mock');
  assert.deepEqual(errors,[],'anonymous route has no runtime JS errors');
  await page.close();

  const game=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const calls=[];
  const match={
   matchId:'11111111-1111-4111-8111-111111111111',
   side:'X',opponent:null,status:'waiting',mode:'invite',
   code:'HJKLMNPQ',board:'.........',turn:'X',version:1,winner:null
  };
  await game.route('**/api/account',async route=>{
   const req=route.request();
   const payload=req.method()==='GET'?{action:'status'}:JSON.parse(req.postData()||'{}');
   calls.push(payload.action);
   const result=payload.action==='status'?{linked:true,profile:{displayName:'Player'}}:
    payload.action==='match:list'?{matches:[]}:
    payload.action==='match:leave'?{ok:true}:
    match;
   await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(result)});
  });
  const gameErrors=[];game.on('pageerror',error=>gameErrors.push(error.message));
  await game.goto(base+'/multiplayer/ttt/');
  await game.locator('#inviteBtn').waitFor({state:'visible'});
  await game.locator('#inviteBtn').click();
  await game.locator('#leaveBtn').waitFor({state:'visible'});
  await game.locator('#leaveBtn').click();
  assert.equal(await game.locator('#leaveDialog').evaluate(el=>el.open),true,
   'leave is a custom accessible dialog');
  assert.equal(calls.includes('match:leave'),false,'no unconfirmed forfeit');
  await game.locator('#keepMatch').click();
  assert.equal(await game.locator('#leaveDialog').evaluate(el=>el.open),false);
  assert.equal(calls.includes('match:leave'),false,'cancel is non-destructive');
  await game.locator('#leaveBtn').click();
  await game.locator('#confirmLeave').click();
  await game.waitForFunction(()=>!document.querySelector('#lobby').hidden);
  assert.equal(calls.filter(x=>x==='match:leave').length,1,'forfeit sent once after confirmation');
  assert.equal(await game.locator('.match-chevron').count(),0,
   'no fake previous matches');
  assert.deepEqual(gameErrors,[],'online game has no page exceptions');
  await game.close();
  console.log('WR_BROWSER_SMOKE_OK anonymous gate, design preservation and real confirmation');
 }finally{await browser.close();server.close()}
})().catch(err=>{console.error('WR_BROWSER_SMOKE_FAILED',err);process.exitCode=1;server.close()});
